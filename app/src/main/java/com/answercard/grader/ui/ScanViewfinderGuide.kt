package com.answercard.grader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState

@Composable
fun ScanViewfinderGuide(
    template: TemplateState,
    modifier: Modifier = Modifier,
) {
    val cardAspectRatio = remember(template) {
        val layout = TemplateGeometry.buildLayout(template)
        TemplateGeometry.renderedWidth(layout) / TemplateGeometry.renderedHeight(layout)
    }
    Canvas(modifier) {
        val rect = ScanGuideGeometry.calculate(size.width, size.height, cardAspectRatio)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
