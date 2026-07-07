package com.answercard.grader.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class PngFileNameTest {
    @Test
    fun fileNameUsesTemplateNameAndTimestamp() {
        val name = PngFileName.forTemplate(
            templateName = "默认试卷",
            timestamp = LocalDateTime.of(2026, 7, 5, 1, 15, 30),
        )

        assertEquals("默认试卷_20260705_011530.png", name)
    }

    @Test
    fun fileNameReplacesCharactersThatAreAwkwardInMediaNames() {
        val name = PngFileName.forTemplate(
            templateName = "月考/化学:一班?",
            timestamp = LocalDateTime.of(2026, 7, 5, 1, 15, 30),
        )

        assertEquals("月考_化学_一班__20260705_011530.png", name)
    }
}
