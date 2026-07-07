package com.answercard.grader.records

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ScanRecordJsonTest {
    @Test
    fun roundTripPreservesRecords() {
        val records = listOf(
            ScanRecord(
                templateId = "template-1",
                templateName = "数学月考",
                examId = "1234",
                totalScore = 26,
                maxScore = 30,
                scannedAt = LocalDateTime.of(2026, 7, 5, 12, 30, 0),
            ),
        )

        val restored = ScanRecordJson.fromJson(ScanRecordJson.toJson(records))

        assertEquals(records, restored)
    }

    @Test
    fun upsertUpdatesSameTemplateAndExamId() {
        val old = ScanRecord(
            templateId = "template-1",
            templateName = "数学月考",
            examId = "1234",
            totalScore = 20,
            maxScore = 30,
            scannedAt = LocalDateTime.of(2026, 7, 5, 12, 0, 0),
        )
        val updated = old.copy(totalScore = 28, scannedAt = LocalDateTime.of(2026, 7, 5, 13, 0, 0))

        val records = ScanRecords.upsert(listOf(old), updated)

        assertEquals(listOf(updated), records)
    }

    @Test
    fun blankJsonReturnsEmptyList() {
        assertEquals(emptyList<ScanRecord>(), ScanRecordJson.fromJson(null))
    }
}
