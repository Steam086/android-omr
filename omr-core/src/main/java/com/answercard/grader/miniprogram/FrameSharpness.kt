package com.answercard.grader.miniprogram

/**
 * Focus / motion-blur metric for a grayscale [MiniProgramFrame].
 *
 * Computes the variance of the discrete Laplacian (4-neighbour kernel) over the interior
 * pixels. A crisp, in-focus frame has strong edges and therefore a high variance; a blurred
 * or out-of-focus frame flattens those edges and scores low. This is the same idea as
 * OpenCV's classic `cv2.Laplacian(img).var()` blur detector, and lets us reject smeared
 * frames before they reach the OMR pipeline.
 */
object FrameSharpness {
    fun laplacianVariance(frame: MiniProgramFrame): Double {
        if (frame.width < 3 || frame.height < 3) return 0.0
        var sum = 0.0
        var sumOfSquares = 0.0
        var count = 0
        for (row in 1 until frame.height - 1) {
            for (column in 1 until frame.width - 1) {
                val laplacian = 4 * frame[row, column] -
                    frame[row - 1, column] -
                    frame[row + 1, column] -
                    frame[row, column - 1] -
                    frame[row, column + 1]
                sum += laplacian
                sumOfSquares += laplacian.toDouble() * laplacian
                count += 1
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return sumOfSquares / count - mean * mean
    }
}
