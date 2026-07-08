package com.answercard.grader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.answercard.grader.template.AddQuestionRequest
import com.answercard.grader.template.EditQuestionRequest
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import com.answercard.grader.template.optionLabels

@Composable
fun TemplateEditSheet(
    template: TemplateState,
    onTemplateChange: (TemplateState) -> Unit,
    onClose: () -> Unit,
    onPrint: () -> Unit,
) {
    var addDialogOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<QuestionSetting?>(null) }
    var deleteTarget by remember { mutableStateOf<QuestionSetting?>(null) }
    var batchDialogOpen by remember { mutableStateOf(false) }
    var examDialogOpen by remember { mutableStateOf(false) }
    val selectedCount = template.questions.count { it.selected }

    MiniBottomFrame(visible = true, onDismiss = onClose, height = 520.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text("x", color = Color(0xFF666666), fontSize = 28.sp, modifier = Modifier.clickable(onClick = onClose))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedCount > 0) "批量操作($selectedCount)" else "批量操作",
                    color = UiTokens.Red,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = selectedCount > 0) { batchDialogOpen = true },
                )
                if (template.showHeader) {
                    Row {
                        Text("准考证号：${template.examIdDigits}位", fontSize = 15.sp, color = UiTokens.TextPrimary)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "修改",
                            color = UiTokens.Red,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { examDialogOpen = true },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("姓名/学号区", fontSize = 15.sp, color = UiTokens.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (template.showHeader) "保留" else "不保留·紧凑",
                        fontSize = 13.sp,
                        color = UiTokens.TextSecondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = template.showHeader,
                        onCheckedChange = { onTemplateChange(template.withShowHeader(it)) },
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp),
            ) {
                template.questions.forEach { question ->
                    EditQuestionRow(
                        question = question,
                        onToggleSelected = { onTemplateChange(template.toggleQuestionSelection(question.number)) },
                        onAnswerChange = { answer -> onTemplateChange(template.toggleAnswer(question.number, answer)) },
                        onScoreChange = { score -> onTemplateChange(template.withScore(question.number, score)) },
                        onEdit = { editTarget = question },
                        onDelete = { deleteTarget = question },
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetAction("印", "打印答题卡", onPrint)
                SheetAction("存", "保存", onClose)
                SheetAction("+", "添加题目") { addDialogOpen = true }
            }
        }
    }

    AddQuestionDialog(
        visible = addDialogOpen,
        template = template,
        onDismiss = { addDialogOpen = false },
        onConfirm = { request ->
            onTemplateChange(template.addQuestions(request))
            addDialogOpen = false
        },
    )

    editTarget?.let { question ->
        EditQuestionDialog(
            question = question,
            onDismiss = { editTarget = null },
            onConfirm = { request ->
                onTemplateChange(template.editQuestion(question.number, request))
                editTarget = null
            },
        )
    }

    deleteTarget?.let { question ->
        MiniConfirmDialog(
            title = "删除题目",
            message = "确认要删除第 ${question.number} 题吗？",
            confirmText = "删除",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onTemplateChange(template.deleteQuestion(question.number))
                deleteTarget = null
            },
        )
    }

    BatchEditDialog(
        visible = batchDialogOpen,
        selectedCount = selectedCount,
        onDismiss = { batchDialogOpen = false },
        onConfirm = { score, optionCount ->
            onTemplateChange(template.batchEditSelectedQuestions(score = score, optionCount = optionCount))
            batchDialogOpen = false
        },
    )

    ExamIdDialog(
        visible = examDialogOpen,
        digits = template.examIdDigits,
        onDismiss = { examDialogOpen = false },
        onConfirm = { digits ->
            onTemplateChange(template.withExamIdDigits(digits))
            examDialogOpen = false
        },
    )
}

@Composable
private fun EditQuestionRow(
    question: QuestionSetting,
    onToggleSelected: () -> Unit,
    onAnswerChange: (String) -> Unit,
    onScoreChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionDot(selected = question.selected, onClick = onToggleSelected)
        Spacer(Modifier.width(8.dp))
        Text("${question.number}", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(32.dp))
        Text("${question.score}分", fontSize = 13.sp, color = UiTokens.TextSecondary, modifier = Modifier.width(42.dp).clickable {
            onScoreChange(question.score + 1)
        })
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            question.options.forEach { option ->
                AnswerPill(label = option, selected = question.answer == option, onClick = { onAnswerChange(option) })
            }
        }
        Text("编辑", color = UiTokens.Green, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onEdit))
        Spacer(Modifier.width(10.dp))
        Text("x", color = UiTokens.Red, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onDelete))
    }
}

@Composable
private fun SelectionDot(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (selected) UiTokens.Red else UiTokens.Separator)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun AddQuestionDialog(
    visible: Boolean,
    template: TemplateState,
    onDismiss: () -> Unit,
    onConfirm: (AddQuestionRequest) -> Unit,
) {
    val defaultStart = (template.questions.maxOfOrNull { it.number } ?: 0) + 1
    var startNumber by remember(visible, defaultStart) { mutableStateOf(defaultStart.toString()) }
    var count by remember(visible) { mutableStateOf("1") }
    var score by remember(visible) { mutableStateOf("2") }
    var optionCount by remember(visible) { mutableStateOf(4) }

    QuestionFormFrame(
        visible = visible,
        title = "添加选题",
        onDismiss = onDismiss,
        errorPrefix = "添加题目",
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniNumberField("起始题号", startNumber, { startNumber = it }, Modifier.weight(1f))
                MiniNumberField("题数", count, { count = it }, Modifier.weight(1f))
                MiniNumberField("分值", score, { score = it }, Modifier.weight(1f))
            }
            TypeAndOptions(optionCount = optionCount, onOptionCountChange = { optionCount = it })
        },
        onConfirm = { setError ->
            val parsedStart = startNumber.toIntOrNull()
            val parsedCount = count.toIntOrNull()
            val parsedScore = score.toIntOrNull()
            when {
                parsedStart == null || parsedStart <= 0 -> setError("请输入正确的题号")
                parsedCount == null || parsedCount <= 0 -> setError("请输入正确的题数")
                parsedScore == null || parsedScore < 0 -> setError("请输入正确的分值")
                else -> onConfirm(
                    AddQuestionRequest(
                        startNumber = parsedStart,
                        count = parsedCount,
                        score = parsedScore,
                        optionCount = optionCount,
                    ),
                )
            }
        },
    )
}

@Composable
private fun EditQuestionDialog(
    question: QuestionSetting,
    onDismiss: () -> Unit,
    onConfirm: (EditQuestionRequest) -> Unit,
) {
    var number by remember(question) { mutableStateOf(question.number.toString()) }
    var score by remember(question) { mutableStateOf(question.score.toString()) }
    var optionCount by remember(question) { mutableStateOf(question.optionCount.coerceIn(2, 4)) }

    QuestionFormFrame(
        visible = true,
        title = "编辑题目",
        onDismiss = onDismiss,
        errorPrefix = "编辑题目",
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniNumberField("题号", number, { number = it }, Modifier.weight(1f))
                MiniNumberField("分值", score, { score = it }, Modifier.weight(1f))
            }
            TypeAndOptions(optionCount = optionCount, onOptionCountChange = { optionCount = it })
        },
        onConfirm = { setError ->
            val parsedNumber = number.toIntOrNull()
            val parsedScore = score.toIntOrNull()
            when {
                parsedNumber == null || parsedNumber <= 0 -> setError("请输入正确的题号")
                parsedScore == null || parsedScore < 0 -> setError("请输入正确的分值")
                else -> onConfirm(
                    EditQuestionRequest(
                        number = parsedNumber,
                        score = parsedScore,
                        optionCount = optionCount,
                    ),
                )
            }
        },
    )
}

@Composable
private fun BatchEditDialog(
    visible: Boolean,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var score by remember(visible) { mutableStateOf("2") }
    var optionCount by remember(visible) { mutableStateOf(4) }

    QuestionFormFrame(
        visible = visible,
        title = "批量修改题目",
        onDismiss = onDismiss,
        errorPrefix = "批量修改",
        content = {
            Text("已选择 $selectedCount 题", fontSize = 15.sp, color = UiTokens.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniNumberField("分值", score, { score = it }, Modifier.weight(1f))
            }
            TypeAndOptions(optionCount = optionCount, onOptionCountChange = { optionCount = it })
        },
        onConfirm = { setError ->
            val parsedScore = score.toIntOrNull()
            when {
                selectedCount <= 0 -> setError("请先选择题目")
                parsedScore == null || parsedScore < 0 -> setError("请输入正确的分值")
                else -> onConfirm(parsedScore, optionCount)
            }
        },
    )
}

@Composable
private fun ExamIdDialog(
    visible: Boolean,
    digits: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(visible, digits) { mutableStateOf(digits.toString()) }
    var error by remember(visible) { mutableStateOf("") }

    MiniCenterFrame(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("修改准考证号", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = UiTokens.TextPrimary)
                Text("x", color = UiTokens.TextSecondary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            MiniNumberField("位数", value, { value = it }, Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = UiTokens.Red, fontSize = 13.sp)
            Button(
                onClick = {
                    val parsed = value.toIntOrNull()
                    if (parsed == null || parsed <= 0) {
                        error = "请输入正确的位数"
                    } else {
                        onConfirm(parsed)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UiTokens.MiniGreen, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("确定", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun QuestionFormFrame(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    errorPrefix: String,
    content: @Composable () -> Unit,
    onConfirm: ((String) -> Unit) -> Unit,
) {
    var errorText by remember(visible, errorPrefix) { mutableStateOf("") }

    MiniCenterFrame(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = UiTokens.TextPrimary)
                Text("x", color = UiTokens.TextSecondary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            content()
            if (errorText.isNotBlank()) Text(errorText, color = UiTokens.Red, fontSize = 13.sp)
            Button(
                onClick = { onConfirm { errorText = it } },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UiTokens.MiniGreen, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("确定", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TypeAndOptions(optionCount: Int, onOptionCountChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("题型:", fontSize = 15.sp, color = UiTokens.TextPrimary)
        DisabledChoice("单选", selected = true)
        DisabledChoice("多选", selected = false)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("选项个数:", fontSize = 15.sp, color = UiTokens.TextPrimary)
        StepperButton("-", enabled = optionCount > 2) { onOptionCountChange(optionCount - 1) }
        Text("$optionCount", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        StepperButton("+", enabled = optionCount < 4) { onOptionCountChange(optionCount + 1) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选项内容:", fontSize = 15.sp, color = UiTokens.TextPrimary)
        optionLabels(optionCount).forEach { label ->
            Box(
                Modifier
                    .width(42.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(UiTokens.SecondaryBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 15.sp, color = UiTokens.TextPrimary)
            }
        }
    }
}

@Composable
private fun MiniNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun DisabledChoice(label: String, selected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) UiTokens.Red else UiTokens.Separator),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        }
        Text(label, color = if (selected) UiTokens.TextPrimary else UiTokens.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 18.sp)
    }
}

@Composable
private fun AnswerPill(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Green, contentColor = Color.White),
            modifier = Modifier.height(32.dp).width(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(32.dp).width(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SheetAction(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp)) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(UiTokens.Separator.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 16.sp, color = UiTokens.TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = UiTokens.TextPrimary)
    }
}
