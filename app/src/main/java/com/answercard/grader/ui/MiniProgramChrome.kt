package com.answercard.grader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiniTopBar(
    title: String,
    modifier: Modifier = Modifier,
    left: (@Composable () -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(UiTokens.StatusBarHeight + UiTokens.NavBarHeight)
            .background(Color.White)
            .padding(top = UiTokens.StatusBarHeight, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            left?.invoke()
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = UiTokens.TextPrimary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            right?.invoke()
        }
    }
}

@Composable
fun MiniBottomFrame(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(240)),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(animationSpec = tween(300)) { it },
                exit = slideOutVertically(animationSpec = tween(240)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (height == null) Modifier else Modifier.height(height))
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Color.White)
                        .clickable(enabled = false) {},
                ) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            title,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = UiTokens.TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    content()
                    Spacer(Modifier.height(UiTokens.HomeIndicatorHeight))
                }
            }
        }
    }
}

@Composable
fun MiniCenterFrame(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(240)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .clickable(enabled = false) {},
            ) {
                content()
            }
        }
    }
}

@Composable
fun MiniConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    BackHandler(onBack = onDismiss)
    MiniCenterFrame(visible = true, onDismiss = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = UiTokens.TextPrimary)
            Text(message, fontSize = 15.sp, color = UiTokens.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (destructive) UiTokens.Red else UiTokens.Green,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}

@Composable
fun MiniCircleIcon(
    label: String,
    color: Color = UiTokens.Green,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
