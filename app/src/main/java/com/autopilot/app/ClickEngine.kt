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
                click(point)
                delay(Random.nextLong(from = 1L, until = 50L))
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

    private companion object {
        const val COOLDOWN_MILLIS = 500L
    }
}