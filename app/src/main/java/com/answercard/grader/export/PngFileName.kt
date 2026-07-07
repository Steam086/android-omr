package com.answercard.grader.export

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PngFileName {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val awkwardChars = Regex("""[\\/:*?"<>|]""")

    fun forTemplate(
        templateName: String,
        timestamp: LocalDateTime = LocalDateTime.now(),
    ): String {
        val safeName = templateName.trim().replace(awkwardChars, "_").ifEmpty { "答题卡" }
        return "${safeName}_${timestamp.format(timestampFormat)}.png"
    }
}
