package com.answercard.grader.records

import android.content.Context

class ScanRecordStore(context: Context) {
    private val preferences = context.getSharedPreferences("scan_records", Context.MODE_PRIVATE)

    fun loadRecords(): List<ScanRecord> =
        ScanRecordJson.fromJson(preferences.getString(KEY_RECORDS, null))

    fun saveRecord(record: ScanRecord) {
        saveRecords(ScanRecords.upsert(loadRecords(), record))
    }

    fun saveRecords(records: List<ScanRecord>) {
        preferences.edit()
            .putString(KEY_RECORDS, ScanRecordJson.toJson(records))
            .apply()
    }

    private companion object {
        const val KEY_RECORDS = "records"
    }
}
