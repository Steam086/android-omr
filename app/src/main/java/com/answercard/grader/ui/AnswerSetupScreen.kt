package com.answercard.grader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState

@Composable
fun AnswerSetupScreen(
    template: TemplateState,
    onTemplateChange: (TemplateState) -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Text("设置答案", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenPreview) {
                Text("预览")
            }
        }

        TemplateControls(
            template = template,
            onTemplateChange = onTemplateChange,
            modifier = Modifier.padding(top = 12.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            template.questions.forEach { question ->
                QuestionAnswerRow(
                    question = question,
                    onAnswerChange = { answer ->
                        onTemplateChange(template.withAnswer(question.number, answer))
                    },
                    onScoreChange = { score ->
                        onTemplateChange(template.withScore(question.number, score))
                    },
                )
            }
        }
    }
}

@Composable
private fun TemplateControls(
    template: TemplateState,
    onTemplateChange: (TemplateState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = template.name,
            onValueChange = { onTemplateChange(template.withName(it)) },
            label = { Text("模板名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("题数：${template.questions.size}，满分：${template.totalScore}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTemplateChange(template.withQuestionCount(template.questions.size - 1)) }) {
                    Text("-")
                }
                OutlinedButton(onClick = { onTemplateChange(template.withQuestionCount(template.questions.size + 1)) }) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
private fun QuestionAnswerRow(
    question: QuestionSetting,
    onAnswerChange: (String) -> Unit,
    onScoreChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0xFFF7F7F7))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${question.number}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onScoreChange(question.score - 1) }) {
                Text("-")
            }
            Text("${question.score}分")
            OutlinedButton(onClick = { onScoreChange(question.score + 1) }) {
                Text("+")
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TemplateGeometry.OPTIONS.forEach { option ->
                FilterChip(
                    selected = question.answer == option,
                    onClick = { onAnswerChange(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
