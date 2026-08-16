package com.autopilot.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptureStats(
    val scanning: Boolean = false,
    val fps: Int = 0,
    val confidence: Float = 0f,
    val clicks: Long = 0L,
    val matchedScale: Float = 1f,
)

object CaptureTelemetry {
    private val _stats = MutableStateFlow(CaptureStats())
    val stats = _stats.asStateFlow()

    fun setScanning(scanning: Boolean) {
        _stats.value = _stats.value.copy(scanning = scanning)
    }

    fun frameProcessed(fps: Int) {
        _stats.value = _stats.value.copy(fps = fps)
    }

    fun detected(confidence: Float, scale: Float) {
        _stats.value = _stats.value.copy(confidence = confidence, matchedScale = scale)
    }

    fun clickRecorded() {
        _stats.value = _stats.value.copy(clicks = _stats.value.clicks + 1)
    }

    fun reset() {
        _stats.value = CaptureStats()
    }
}