package com.answercard.grader.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.answercard.grader.export.PngShare
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareTemplateScreen(template: TemplateState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(template) {
        preview = withContext(Dispatchers.Default) {
            TemplateRenderer.render(template, scale = 2f).asImageBitmap()
        }
    }
    Column(Modifier.fillMaxSize().background(UiTokens.SecondaryBackground)) {
        MiniTopBar(
            title = "",
            left = { Text("< 返回", fontSize = 16.sp, color = UiTokens.TextPrimary, modifier = Modifier.clickable(onClick = onBack)) },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        template.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Badge("${template.questions.size}题")
                    Badge(if (template.showHeader) "${template.examIdDigits}位考号" else "无考号")
                }
                preview?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "分享答题卡预览",
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).background(Color.White),
                    )
                }
                answerRows(template).forEach {
                    Text(it, fontSize = 15.sp, color = UiTokens.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        val intent = withContext(Dispatchers.IO) { PngShare.createShareIntent(context, template) }
                        context.startActivity(Intent.createChooser(intent, "分享模板"))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = UiTokens.Green),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text("分享模板到好友/群聊", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(UiTokens.Blue).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun answerRows(template: TemplateState): List<String> =
    template.questions.chunked(5).map { group ->
        val first = group.first().number
        val last = group.last().number
        "$first~$last    ${group.joinToString(" ") { it.answer.ifBlank { "-" } }}"
    }
