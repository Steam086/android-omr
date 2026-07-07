package com.answercard.grader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.answercard.grader.records.ScanRecord
import com.answercard.grader.records.ScanRecordStore
import java.time.format.DateTimeFormatter

@Composable
fun RecordsScreen(
    templateId: String,
    templateName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val records = remember(templateId) {
        ScanRecordStore(context.applicationContext)
            .loadRecords()
            .filter { it.templateId == templateId }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Text("成绩记录", style = MaterialTheme.typography.titleLarge)
        }
        Text(templateName, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)

        if (records.isEmpty()) {
            Text(
                "暂无成绩记录。填涂考号后扫描，成绩会自动保存。",
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                records.forEach { record ->
                    RecordRow(record)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: ScanRecord) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("考号 ${record.examId}", style = MaterialTheme.typography.titleMedium)
        Text("分数 ${record.totalScore}/${record.maxScore}")
        Text(record.scannedAt.format(formatter), style = MaterialTheme.typography.bodySmall)
    }
}
