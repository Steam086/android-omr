package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidRequiredCellValidatorTest {
    @Test
    fun requiredCellOutsideInterpolationMarginIsClipped() {
        val validation = AndroidRequiredCellValidator.validate(
            frame = whiteFrame(),
            cells = projectedCellsWithSingleQuestion(rectangularCell(0.5, 10.0, 12.0, 10.0)),
        )

        assertEquals(RequiredCellFailure.CLIPPED, validation.failure)
    }

    @Test
    fun requiredCellInsideFrameButBelowInformationFloorIsSmall() {
        val validation = AndroidRequiredCellValidator.validate(
            frame = whiteFrame(),
            cells = projectedCellsWithSingleQuestion(rectangularCell(10.0, 10.0, 9.0, 8.0)),
        )

        assertEquals(RequiredCellFailure.TOO_SMALL, validation.failure)
    }

    @Test
    fun requiredCellAtInformationFloorIsAccepted() {
        val validation = AndroidRequiredCellValidator.validate(
            frame = whiteFrame(),
            cells = projectedCellsWithSingleQuestion(rectangularCell(10.0, 10.0, 10.0, 8.0)),
        )

        assertNull(validation.failure)
    }

    private fun whiteFrame() = MiniProgramFrame(100, 100, IntArray(10_000) { 255 })

    private fun projectedCellsWithSingleQuestion(cell: MiniProgramCell) =
        AndroidPaperProjectedCells(
            questionCells = mapOf(AndroidPaperQuestionCellKey(0, 0) to cell),
            admissionNumberCells = emptyMap(),
            debugInfo = emptyList(),
        )

    private fun rectangularCell(left: Double, top: Double, width: Double, height: Double) =
        MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(top, left),
            rightTop = MiniProgramGridPoint(top, left + width),
            leftBottom = MiniProgramGridPoint(top + height, left),
            rightBottom = MiniProgramGridPoint(top + height, left + width),
        )
}
