package com.autopilot.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small always-on-top control surface. It deliberately stays native so it can
 * remain responsive while the Compose activity is backgrounded.
 */
class FloatingControlPanel(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panel: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var statusRing: View? = null
    private var pulseAnimator: ValueAnimator? = null

    fun showDefault(user: User, onAction: (PanelAction) -> Unit) {
        if (panel != null) return

        val root = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 12, 19, 31))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(90, 113, 231, 205))
            }
            elevation = dp(12).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content, FrameLayout.LayoutParams(dp(260), dp(if (user.shouldShowOverlayAd) 142 else 86)))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logoFrame = FrameLayout(context)
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.ic_autopilot)
            contentDescription = "AUTOPILOT badge"
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        val ring = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.rgb(99, 230, 190))
            }
        }
        logoFrame.addView(ring, FrameLayout.LayoutParams(dp(42), dp(42)))
        logoFrame.addView(logo, FrameLayout.LayoutParams(dp(42), dp(42)))
        statusRing = ring
        header.addView(logoFrame, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(context).apply {
            text = "AUTOPILOT\nLIVE CONTROL"
            setTextColor(Color.WHITE)
            textSize = 10f
            letterSpacing = 0.12f
            setPadding(dp(5), 0, dp(4), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(controlButton(R.drawable.ic_play, Color.rgb(99, 230, 190), "Start") {
            startPulse()
            onAction(PanelAction.START)
        })
        header.addView(controlButton(R.drawable.ic_pause, Color.rgb(255, 206, 106), "Pause") {
            stopPulse()
            onAction(PanelAction.PAUSE)
        })
        header.addView(controlButton(R.drawable.ic_stop, Color.rgb(255, 125, 138), "Stop") {
            stopPulse()
            onAction(PanelAction.STOP)
        })
        content.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        if (user.shouldShowOverlayAd) {
            val adFrame = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(100, 35, 52, 69))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.argb(55, 255, 255, 255))
                }
                clipToOutline = true
            }
            val ad = WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                loadUrl(AdService.TARGET_URL)
            }
            adFrame.addView(ad, FrameLayout.LayoutParams(-1, dp(52)))
            content.addView(adFrame, LinearLayout.LayoutParams(-1, dp(54)).apply {
                topMargin = dp(6)
            })
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
        }
        val windowParams = WindowManager.LayoutParams(
            dp(276),
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth() - dp(292)
            y = dp(96)
        }

        attachDrag(root, header, windowParams)
        windowManager.addView(root, windowParams)
        panel = root
        params = windowParams
        startPulse()
    }

    fun dismiss() {
        stopPulse()
        panel?.let {
            it.findWebView()?.destroy()
            runCatching { windowManager.removeView(it) }
        }
        panel = null
        params = null
    }

    private fun controlButton(icon: Int, tint: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            setColorFilter(tint)
            contentDescription = description
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(32, Color.red(tint), Color.green(tint), Color.blue(tint)))
            }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginStart = dp(3)
            }
        }

    private fun attachDrag(view: View, handle: View, windowParams: WindowManager.LayoutParams) {
        var downX = 0
        var downY = 0
        var originX = 0
        var originY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX.toInt()
                    downY = event.rawY.toInt()
                    originX = windowParams.x
                    originY = windowParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    windowParams.x = originX + event.rawX.toInt() - downX
                    windowParams.y = (originY + event.rawY.toInt() - downY).coerceAtLeast(dp(30))
                    runCatching { windowManager.updateViewLayout(view, windowParams) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    snapToEdge(view, windowParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, windowParams: WindowManager.LayoutParams) {
        val target = if (windowParams.x + view.width / 2 < screenWidth() / 2) {
            dp(12)
        } else {
            screenWidth() - view.width - dp(12)
        }.coerceAtLeast(dp(12))
        val start = windowParams.x
        ValueAnimator.ofInt(start, target).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                windowParams.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, windowParams) }
            }
            start()
        }
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.45f, 1f).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { statusRing?.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        statusRing?.alpha = 0.35f
    }

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun View.findWebView(): WebView? {
        if (this is WebView) return this
        if (this !is android.view.ViewGroup) return null
        for (index in 0 until childCount) {
            childViews(index).findWebView()?.let { return it }
        }
        return null
    }

    private fun android.view.ViewGroup.childViews(index: Int): View = getChildAt(index)

    enum class PanelAction {
        START,
        PAUSE,
        STOP,
    }
}