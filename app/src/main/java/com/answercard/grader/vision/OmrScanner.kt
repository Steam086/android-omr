package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.grading.GradeResult
import com.answercard.grader.grading.Grader
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.TemplateState

data class OmrScanResult(
    val examId: String?,
    val answers: Map<Int, String?>,
    val grade: GradeResult,
    val corners: DetectedCorners,
    val normalizedBitmap: Bitmap,
)

object OmrScanner {
    private val CAMERA_ORIENTATIONS = listOf(0, 90, 180, 270)

    fun scan(
        bitmap: Bitmap,
        template: TemplateState,
        layout: CardLayout,
        scale: Float = 3f,
    ): OmrScanResult? {
        return CAMERA_ORIENTATIONS
            .mapNotNull { degrees -> scanOrientation(bitmap, template, layout, scale, degrees) }
            .maxByOrNull { it.confidence }
            ?.result
    }

    private fun scanOrientation(
        bitmap: Bitmap,
        template: TemplateState,
        layout: CardLayout,
        scale: Float,
        degrees: Int,
    ): Candidate? {
        val oriented = rotate(bitmap, degrees)
        val corners = CornerDetector.detect(oriented, layout) ?: return null
        val normalized = PerspectiveWarp.normalize(oriented, corners, layout, scale)
        val answers = AnswerReader.readAnswers(normalized, layout, scale)
        val examId = ExamIdReader.readExamId(normalized, layout, scale)
        val grade = Grader.grade(
            template = template,
            recognizedAnswers = answers.filterValues { it != null }.mapValues { it.value ?: "" },
        )

        return Candidate(
            result = OmrScanResult(
                examId = examId,
                answers = answers,
                grade = grade,
                corners = corners,
                normalizedBitmap = normalized,
            ),
            confidence = confidence(examId, answers),
        )
    }

    private fun confidence(examId: String?, answers: Map<Int, String?>): Int {
        val examIdScore = if (examId.isNullOrBlank()) 0 else examId.length * 100
        val answeredScore = answers.count { it.value != null } * 10
        return examIdScore + answeredScore
    }

    private data class Candidate(
        val result: OmrScanResult,
        val confidence: Int,
    )

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        return when (normalized) {
            0 -> bitmap
            90 -> rotate90(bitmap)
            180 -> rotate180(bitmap)
            270 -> rotate270(bitmap)
            else -> bitmap
        }
    }

    private fun rotate90(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.height, bitmap.width, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.WHITE)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                output.setPixel(bitmap.height - 1 - y, x, bitmap.getPixel(x, y))
            }
        }
        return output
    }

    private fun rotate180(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.WHITE)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                output.setPixel(bitmap.width - 1 - x, bitmap.height - 1 - y, bitmap.getPixel(x, y))
            }
        }
        return output
    }

    private fun rotate270(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.height, bitmap.width, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.WHITE)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                output.setPixel(y, bitmap.width - 1 - x, bitmap.getPixel(x, y))
            }
        }
        return output
    }
}
