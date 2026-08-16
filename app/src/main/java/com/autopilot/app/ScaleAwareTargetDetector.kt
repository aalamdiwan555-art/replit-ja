package com.autopilot.app

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Multi-scale template matcher for size-invariant target detection.
 *
 * Matching is performed on grayscale Mats to keep the Default dispatcher work
 * small enough for a live ImageReader stream. All returned coordinates remain
 * in the original frame coordinate space.
 */
class ScaleAwareTargetDetector {
    fun findBest(
        frame: Bitmap,
        targetTemplate: Bitmap,
        threshold: Float = MATCH_THRESHOLD,
    ): Detection? {
        val frameMat = Mat()
        val templateMat = Mat()
        return try {
            val frameGray = Mat()
            val templateGray = Mat()
            Utils.bitmapToMat(frame, frameMat)
            Utils.bitmapToMat(targetTemplate, templateMat)
            try {
                Imgproc.cvtColor(frameMat, frameGray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_RGBA2GRAY)

                var best: Detection? = null
                MATCH_SCALES.forEach { scale ->
                    val scaled = Mat()
                    val result = Mat()
                    try {
                        val width = (templateGray.cols() * scale).roundToInt()
                        val height = (templateGray.rows() * scale).roundToInt()
                        if (width < MIN_TEMPLATE_EDGE || height < MIN_TEMPLATE_EDGE ||
                            width >= frameGray.cols() || height >= frameGray.rows()
                        ) {
                            return@forEach
                        }
                        Imgproc.resize(templateGray, scaled, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                        Imgproc.matchTemplate(frameGray, scaled, result, Imgproc.TM_CCOEFF_NORMED)
                        val max = Core.minMaxLoc(result)
                        if (max.maxVal >= threshold &&
                            (best == null || max.maxVal > best!!.confidence)
                        ) {
                            val left = max.maxLoc.x.roundToInt()
                            val top = max.maxLoc.y.roundToInt()
                            best = Detection(
                                bounds = Rect(left, top, left + width, top + height),
                                confidence = max.maxVal.toFloat(),
                                scale = scale.toFloat(),
                            )
                        }
                    } finally {
                        result.release()
                        scaled.release()
                    }
                }
                best
            } finally {
                frameGray.release()
                templateGray.release()
            }
        } finally {
            frameMat.release()
            templateMat.release()
        }
    }

    data class Detection(
        val bounds: Rect,
        val confidence: Float,
        val scale: Float,
    ) {
        val centerX: Int
            get() = bounds.centerX()

        val centerY: Int
            get() = bounds.centerY()

        val center: android.graphics.Point
            get() = android.graphics.Point(centerX, centerY)
    }

    companion object {
        // Deliberately inside the requested 65%–70% band.
        const val MATCH_THRESHOLD = 0.68f
        val MATCH_SCALES = listOf(0.50, 0.67, 0.75, 1.0, 1.25, 1.5, 1.75)

        private const val MIN_TEMPLATE_EDGE = 4
    }
}