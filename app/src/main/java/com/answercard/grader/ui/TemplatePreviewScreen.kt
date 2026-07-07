package com.answercard.grader.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.answercard.grader.export.PngExporter
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TemplatePreviewScreen(
    template: TemplateState,
    editSheetOpen: Boolean,
    onTemplateChange: (TemplateState) -> Unit,
    onEditSheetChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    val imageBitmap = remember(template) { TemplateRenderer.render(template, scale = 3f).asImageBitmap() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(UiTokens.SecondaryBackground)) {
            MiniTopBar(
                title = template.name,
                left = { Text("< 返回", fontSize = 16.sp, color = UiTokens.TextPrimary, modifier = Modifier.clickable(onClick = onBack)) },
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("+", fontSize = 24.sp, color = UiTokens.TextPrimary, modifier = Modifier.clickable {
                            scale = (scale * 1.2f).coerceAtMost(1.4f)
                        })
                        Text("-", fontSize = 24.sp, color = UiTokens.TextPrimary, modifier = Modifier.clickable {
                            scale = (scale * 0.8f).coerceAtLeast(0.55f)
                        })
                    }
                },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(UiTokens.SecondaryBackground)
                    .padding(12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "答题卡模板预览",
                    modifier = Modifier
                        .fillMaxWidth(scale.coerceIn(0.55f, 1.4f))
                        .background(Color.White)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp + UiTokens.HomeIndicatorHeight)
                    .background(Color.White)
                    .padding(start = 18.dp, end = 18.dp, bottom = UiTokens.HomeIndicatorHeight),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewBottomButton("分享", onShare, Modifier.weight(1f))
                PreviewBottomButton("编辑", { onEditSheetChange(true) }, Modifier.weight(1f))
                PreviewBottomButton(
                    "打印",
                    {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { PngExporter.saveTemplatePng(context, template) }
                            }.onSuccess {
                                Toast.makeText(context, "已保存图片", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    Modifier.weight(1f),
                )
            }
        }

        if (editSheetOpen) {
            TemplateEditSheet(
                template = template,
                onTemplateChange = onTemplateChange,
                onClose = { onEditSheetChange(false) },
                onPrint = {
                    onEditSheetChange(false)
                    scope.launch {
                        withContext(Dispatchers.IO) { PngExporter.saveTemplatePng(context, template) }
                    }
                },
            )
        }
    }
}

@Composable
private fun PreviewBottomButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = UiTokens.Green),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
