package com.autopilot.app

import android.graphics.Bitmap
import android.graphics.Point
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Multi-scale template matcher for mini, small, normal, and large targets.
 * The returned points are always in the original frame coordinate space.
 */
class ScaleAwareTargetDetector {
    fun find(
        frame: Bitmap,
        targetTemplate: Bitmap,
        threshold: Double = DEFAULT_THRESHOLD,
    ): List<Point> {
        val frameMat = Mat()
        val templateMat = Mat()
        return try {
            Utils.bitmapToMat(frame, frameMat)
            Utils.bitmapToMat(targetTemplate, templateMat)
            val matches = mutableListOf<Match>()
            SCALES.forEach { scale ->
                val scaled = Mat()
                try {
                    val width = (templateMat.cols() * scale).toInt()
                    val height = (templateMat.rows() * scale).toInt()
                    if (width < MIN_TEMPLATE_EDGE || height < MIN_TEMPLATE_EDGE ||
                        width >= frameMat.cols() || height >= frameMat.rows()
                    ) {
                        return@forEach
                    }
                    Imgproc.resize(templateMat, scaled, Size(width.toDouble(), height.toDouble()))
                    val result = Mat()
                    try {
                        Imgproc.matchTemplate(frameMat, scaled, result, Imgproc.TM_CCOEFF_NORMED)
                        val max = Core.minMaxLoc(result)
                        if (max.maxVal >= threshold) {
                            matches += Match(
                                x = max.maxLoc.x.toInt() + width / 2,
                                y = max.maxLoc.y.toInt() + height / 2,
                                radius = maxOf(width, height) / 2,
                                score = max.maxVal,
                            )
                        }
                    } finally {
                        result.release()
                    }
                } finally {
                    scaled.release()
                }
            }
            matches
                .sortedByDescending { it.score }
                .fold(mutableListOf()) { unique, match ->
                    if (unique.none { distanceSquared(it.x, it.y, match.x, match.y) < match.radius * match.radius }) {
                        unique += Point(match.x, match.y)
                    }
                    unique
                }
        } finally {
            frameMat.release()
            templateMat.release()
        }
    }

    private data class Match(
        val x: Int,
        val y: Int,
        val radius: Int,
        val score: Double,
    )

    private companion object {
        val SCALES = listOf(0.35, 0.5, 0.67, 0.8, 1.0, 1.25, 1.5, 1.75, 2.0)
        const val MIN_TEMPLATE_EDGE = 4
        const val DEFAULT_THRESHOLD = 0.86

        fun distanceSquared(x1: Int, y1: Int, x2: Int, y2: Int): Long {
            val dx = (x1 - x2).toLong()
            val dy = (y1 - y2).toLong()
            return dx * dx + dy * dy
        }
    }
}