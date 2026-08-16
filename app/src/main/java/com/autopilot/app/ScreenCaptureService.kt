package com.autopilot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var storage: SecureStorage
    private lateinit var networkTime: NetworkTimeProvider
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var expiryHandler: Handler? = null
    private var floatingPanel: FloatingControlPanel? = null
    private var targetTemplate: Bitmap? = null
    private var paused = false

    private val detector = ScaleAwareTargetDetector()
    private val clickEngine = ClickEngine()
    private val processingFrame = AtomicBoolean(false)
    private var fpsWindowStart = 0L
    private var framesInWindow = 0

    override fun onCreate() {
        super.onCreate()
        storage = SecureStorage(applicationContext)
        networkTime = NetworkTimeProvider(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = !paused
                CaptureTelemetry.setScanning(!paused)
                return START_NOT_STICKY
            }
        }

        val user = storage.getUser(networkTime.currentTimeMillis())
        if (!user.hasActiveAccess) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (mediaProjection == null && intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA)
            }
            if (resultCode != -1 && data != null) startCapture(resultCode, data)
        }
        scheduleExpiryCheck()
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi.coerceAtLeast(1)

        targetTemplate = loadTargetTemplate()
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        captureThread = HandlerThread("autopilot-capture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            var paddedBitmap: Bitmap? = null
            var frame: Bitmap? = null
            try {
                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val rowPadding = max(0, plane.rowStride - plane.pixelStride * width)
                val paddedWidth = width + rowPadding / plane.pixelStride
                paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                paddedBitmap.copyPixelsFromBuffer(plane.buffer)
                frame = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)

                if (processingFrame.compareAndSet(false, true)) {
                    val frameForAnalysis = frame
                    frame = null
                    serviceScope.launch {
                        try {
                            analyzeFrame(frameForAnalysis)
                        } finally {
                            frameForAnalysis.recycle()
                            processingFrame.set(false)
                        }
                    }
                }
            } catch (_: RuntimeException) {
                // Some devices can provide a truncated buffer while the
                // projection is being torn down. Resources are still closed.
            } finally {
                image.close()
                frame?.recycle()
                paddedBitmap?.recycle()
            }
        }, captureHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AUTOPILOT",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )

        floatingPanel = FloatingControlPanel(this).also { panel ->
            panel.showDefault(storage.getUser(networkTime.currentTimeMillis())) { action ->
                when (action) {
                    FloatingControlPanel.PanelAction.START -> paused = false
                    FloatingControlPanel.PanelAction.PAUSE -> paused = !paused
                    FloatingControlPanel.PanelAction.STOP -> stopSelf()
                }
                CaptureTelemetry.setScanning(!paused)
            }
        }
        CaptureTelemetry.setScanning(true)
    }

    /**
     * This method is always invoked by the service's Default dispatcher scope.
     * A single in-flight frame prevents a slow match from building a backlog.
     */
    private fun analyzeFrame(bitmap: Bitmap) {
        if (paused || bitmap.width == 0 || bitmap.height == 0) return
        val now = SystemClock.elapsedRealtime()
        if (fpsWindowStart == 0L || now - fpsWindowStart >= FPS_WINDOW_MILLIS) {
            CaptureTelemetry.frameProcessed(
                (framesInWindow * 1_000L / (now - fpsWindowStart).coerceAtLeast(1L)).toInt(),
            )
            fpsWindowStart = now
            framesInWindow = 0
        }
        framesInWindow++

        val template = targetTemplate ?: return
        val detection = detector.findBest(bitmap, template) ?: return
        CaptureTelemetry.detected(detection.confidence, detection.scale)
        serviceScope.launch {
            clickEngine.run(listOf(detection.center)) { point ->
                if (AutopilotAccessibilityService.clickAt(point)) {
                    CaptureTelemetry.clickRecorded()
                }
            }
        }
    }

    private fun loadTargetTemplate(): Bitmap? {
        val drawable = AppCompatResources.getDrawable(this, R.drawable.ic_autopilot) ?: return null
        val size = dp(TEMPLATE_SIZE_DP)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun scheduleExpiryCheck() {
        if (expiryHandler == null) expiryHandler = Handler(mainLooper)
        expiryHandler?.removeCallbacksAndMessages(null)
        expiryHandler?.postDelayed(object : Runnable {
            override fun run() {
                val user = storage.getUser(networkTime.currentTimeMillis())
                if (!user.hasActiveAccess) {
                    stopSelf()
                } else {
                    expiryHandler?.postDelayed(this, EXPIRY_CHECK_MILLIS)
                }
            }
        }, EXPIRY_CHECK_MILLIS)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_autopilot)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Multi-scale screen matching is active")
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notification_channel_description) },
        )
    }

    override fun onDestroy() {
        expiryHandler?.removeCallbacksAndMessages(null)
        floatingPanel?.dismiss()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        captureThread?.quitSafely()
        targetTemplate?.recycle()
        targetTemplate = null
        captureHandler = null
        captureThread = null
        CaptureTelemetry.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PAUSE = "com.autopilot.app.action.PAUSE"
        const val ACTION_STOP = "com.autopilot.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL_ID = "autopilot_capture"
        private const val NOTIFICATION_ID = 20
        private const val EXPIRY_CHECK_MILLIS = 10_000L
        private const val FPS_WINDOW_MILLIS = 1_000L
        private const val TEMPLATE_SIZE_DP = 48
    }
}