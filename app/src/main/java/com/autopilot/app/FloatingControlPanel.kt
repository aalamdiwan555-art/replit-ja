package com.autopilot.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable

class FloatingControlPanel(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panel: View? = null

    fun show(view: View) {
        if (panel != null) return
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 96
        }
        windowManager.addView(view, params)
        panel = view
    }

    fun showDefault(onAction: (PanelAction) -> Unit) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(232, 12, 28, 45))
                cornerRadius = dp(18).toFloat()
            }
        }
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.ic_autopilot)
            contentDescription = "AUTOPILOT"
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        container.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        listOf(
            Triple(android.R.drawable.ic_media_play, Color.rgb(85, 214, 190), PanelAction.START),
            Triple(android.R.drawable.ic_media_pause, Color.rgb(255, 206, 106), PanelAction.PAUSE),
            Triple(android.R.drawable.ic_media_stop, Color.rgb(255, 125, 138), PanelAction.STOP),
        ).forEach { (icon, tint, action) ->
            container.addView(ImageButton(context).apply {
                setImageResource(icon)
                setColorFilter(tint)
                contentDescription = action.name
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { onAction(action) }
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        container.addView(TextView(context).apply {
            text = "•"
            setTextColor(Color.rgb(85, 214, 190))
            textSize = 22f
            contentDescription = "Status"
        }, LinearLayout.LayoutParams(dp(20), dp(42)))
        show(container)
    }

    fun dismiss() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    enum class PanelAction {
        START,
        PAUSE,
        STOP,
    }
}