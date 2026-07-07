package com.answercard.grader.records

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

object ScanRecordJson {
    fun toJson(records: List<ScanRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("templateId", record.templateId)
                    .put("templateName", record.templateName)
                    .put("examId", record.examId)
                    .put("totalScore", record.totalScore)
                    .put("maxScore", record.maxScore)
                    .put("scannedAt", record.scannedAt.toString()),
            )
        }
        return array.toString()
    }

    fun fromJson(json: String?): List<ScanRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ScanRecord(
                    templateId = item.getString("templateId"),
                    templateName = item.getString("templateName"),
                    examId = item.getString("examId"),
                    totalScore = item.getInt("totalScore"),
                    maxScore = item.getInt("maxScore"),
                    scannedAt = LocalDateTime.parse(item.getString("scannedAt")),
                )
            }.sortedByDescending { it.scannedAt }
        }.getOrDefault(emptyList())
    }
}
