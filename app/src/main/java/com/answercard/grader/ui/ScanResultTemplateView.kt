package com.answercard.grader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScanResultTemplateView(
    template: TemplateState,
    result: ScanDisplayResult?,
    modifier: Modifier = Modifier,
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val overlay = remember(template, result) { ScanTemplateOverlay.from(template, result) }

    LaunchedEffect(template) {
        imageBitmap = withContext(Dispatchers.Default) {
            TemplateRenderer.render(template, scale = 2f).asImageBitmap()
        }
    }

    Box(
        modifier
            .aspectRatio(overlay.renderedWidth / overlay.renderedHeight)
            .background(Color.White),
    ) {
        imageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "答题卡识别结果",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            overlay.answerRects.forEach { mark ->
                drawAnswerMark(mark, overlay)
            }
            overlay.admissionRects.forEach { mark ->
                drawAdmissionMark(mark, overlay)
            }
        }
    }
}

private fun DrawScope.drawAnswerMark(
    mark: ScanAnswerOverlayRect,
    overlay: ScanTemplateOverlay,
) {
    val color = when (mark.state) {
        ScanAnswerMarkState.MARKED_CORRECT -> Color(0xFF2E7D32)
        ScanAnswerMarkState.MARKED_WRONG -> Color(0xFFC62828)
        ScanAnswerMarkState.MISSED_CORRECT -> Color(0xFF2E7D32)
        ScanAnswerMarkState.MARKED_UNKNOWN -> Color(0xFF1565C0)
        ScanAnswerMarkState.UNMARKED -> return
    }
    val strokeWidth = when (mark.state) {
        ScanAnswerMarkState.MISSED_CORRECT -> 2.4.dp.toPx()
        else -> 2.dp.toPx()
    }
    drawOverlayRect(
        rect = mark.rect,
        overlay = overlay,
        color = color,
        fillAlpha = if (mark.state == ScanAnswerMarkState.MISSED_CORRECT) 0f else 0.28f,
        strokeAlpha = 0.95f,
        strokeWidth = strokeWidth,
    )
}

private fun DrawScope.drawAdmissionMark(
    mark: ScanAdmissionOverlayRect,
    overlay: ScanTemplateOverlay,
) {
    val color = when (mark.state) {
        ScanAdmissionMarkState.SELECTED -> Color(0xFF1565C0)
        ScanAdmissionMarkState.MARKED -> Color(0xFF455A64)
        ScanAdmissionMarkState.CONFLICT -> Color(0xFFF9A825)
    }
    drawOverlayRect(
        rect = mark.rect,
        overlay = overlay,
        color = color,
        fillAlpha = 0.24f,
        strokeAlpha = 0.95f,
        strokeWidth = 2.dp.toPx(),
    )
}

private fun DrawScope.drawOverlayRect(
    rect: Rect,
    overlay: ScanTemplateOverlay,
    color: Color,
    fillAlpha: Float,
    strokeAlpha: Float,
    strokeWidth: Float,
) {
    val left = rect.x / overlay.renderedWidth * size.width
    val top = rect.y / overlay.renderedHeight * size.height
    val width = rect.w / overlay.renderedWidth * size.width
    val height = rect.h / overlay.renderedHeight * size.height
    val topLeft = Offset(left, top)
    val drawSize = Size(width, height)
    val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

    if (fillAlpha > 0f) {
        drawRoundRect(
            color = color.copy(alpha = fillAlpha),
            topLeft = topLeft,
            size = drawSize,
            cornerRadius = radius,
        )
    }
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha),
        topLeft = topLeft,
        size = drawSize,
        cornerRadius = radius,
        style = Stroke(width = strokeWidth),
    )
}
