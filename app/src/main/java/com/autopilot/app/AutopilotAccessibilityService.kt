package com.autopilot.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * System input bridge used by the detector. Android only permits gesture
 * dispatch after the user explicitly enables this service in Accessibility.
 */
class AutopilotAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun clickAt(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MILLIS))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        private var instance: AutopilotAccessibilityService? = null

        fun clickAt(point: android.graphics.Point): Boolean =
            instance?.clickAt(point.x, point.y) == true

        private const val TAP_DURATION_MILLIS = 24L
    }
}