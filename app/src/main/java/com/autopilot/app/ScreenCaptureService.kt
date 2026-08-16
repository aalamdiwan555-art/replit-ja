package com.autopilot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
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
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
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
    private var paused = false

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
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        captureThread = HandlerThread("autopilot-capture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            var bitmap: Bitmap? = null
            try {
                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val frame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                serviceScope.launch {
                    try {
                        analyzeFrame(frame)
                    } finally {
                        frame.recycle()
                    }
                }
            } finally {
                image.close()
                bitmap?.recycle()
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
            panel.showDefault { action ->
                when (action) {
                    FloatingControlPanel.PanelAction.START -> paused = false
                    FloatingControlPanel.PanelAction.PAUSE -> paused = !paused
                    FloatingControlPanel.PanelAction.STOP -> stopSelf()
                }
            }
        }
    }

    private fun analyzeFrame(bitmap: Bitmap) {
        if (paused || bitmap.width == 0 || bitmap.height == 0) return
        // OpenCV/Bitmap target matching remains isolated from the capture lifecycle.
        // Any future detector can dispatch ClickEngine.run from this Default context.
    }

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
            .setContentText("Screen capture is active")
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
        captureHandler = null
        captureThread = null
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
    }
}