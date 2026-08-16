package com.autopilot.app

import android.graphics.Point
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.delay

enum class ClickState {
    IDLE,
    DETECTED,
    CLICKING,
    COOLDOWN,
}

class ClickEngine {
    private val state = AtomicReference(ClickState.IDLE)

    fun currentState(): ClickState = state.get()

    suspend fun run(points: List<Point>, click: suspend (Point) -> Unit) {
        if (points.isEmpty() || !state.compareAndSet(ClickState.IDLE, ClickState.DETECTED)) return
        try {
            state.set(ClickState.CLICKING)
            points.forEach { point ->
                // Keep the delay immediately before dispatch so detection feels
                // instant while the input still has a small humanized variance.
                delay(Random.nextInt(1, 51).toLong())
                click(point.humanized())
            }
        } finally {
            state.set(ClickState.COOLDOWN)
            try {
                delay(COOLDOWN_MILLIS)
            } finally {
                state.set(ClickState.IDLE)
            }
        }
    }

    private fun Point.humanized(): Point {
        fun jitter(): Int {
            val magnitude = Random.nextInt(2, 6)
            return if (Random.nextBoolean()) magnitude else -magnitude
        }
        return Point(x + jitter(), y + jitter())
    }

    private companion object {
        const val COOLDOWN_MILLIS = 500L
    }
}