package com.answercard.grader.records

import java.time.LocalDateTime

data class ScanRecord(
    val templateId: String,
    val templateName: String,
    val examId: String,
    val totalScore: Int,
    val maxScore: Int,
    val scannedAt: LocalDateTime,
)

object ScanRecords {
    fun upsert(records: List<ScanRecord>, record: ScanRecord): List<ScanRecord> {
        val replaced = records.filterNot {
            it.templateId == record.templateId && it.examId == record.examId
        }
        return (listOf(record) + replaced).sortedByDescending { it.scannedAt }
    }
}
