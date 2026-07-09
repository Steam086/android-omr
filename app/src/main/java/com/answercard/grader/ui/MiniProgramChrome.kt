package com.answercard.grader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            .background(Color.White)
            .statusBarsPadding()
            .height(UiTokens.NavBarHeight)
            .padding(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            left?.invoke()
        }
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = UiTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.6f),
        )
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
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (visible || visibleState.currentState) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(240)),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(animationSpec = tween(300)) { it },
                        exit = slideOutVertically(animationSpec = tween(240)) { it },
                    )
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color.White)
                    .clickable(enabled = false) {}
                    .navigationBarsPadding()
                    .imePadding()
                    .then(if (height == null) Modifier else Modifier.height(height)),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        title,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = UiTokens.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                content()
                Spacer(Modifier.height(12.dp))
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
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (visible || visibleState.currentState) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(240)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss)
                .imePadding(),
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
    visible: Boolean = true,
) {
    MiniCenterFrame(visible = visible, onDismiss = onDismiss) {
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
