package com.answercard.grader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.answercard.grader.template.StoredTemplate
import com.answercard.grader.template.TemplateCollection
import com.answercard.grader.template.TemplateState
import com.answercard.grader.template.TemplateStore
import java.time.LocalDate
import java.util.UUID

@Composable
fun AnswerCardApp() {
    val context = LocalContext.current
    val store = remember { TemplateStore(context.applicationContext) }
    var screen by remember { mutableStateOf(Screen.TemplateList) }
    var collection by remember { mutableStateOf(store.loadCollection()) }
    var editSheetOpen by remember { mutableStateOf(false) }
    val selected = collection.selectedTemplate
    val template = selected.template

    fun updateCollection(next: TemplateCollection) {
        collection = next
        store.saveCollection(next)
    }

    fun updateSelectedTemplate(next: TemplateState) {
        updateCollection(collection.upsert(selected.copy(template = next)))
    }

    fun openHome() {
        editSheetOpen = false
        screen = Screen.TemplateList
    }

    BackHandler(enabled = screen != Screen.TemplateList || editSheetOpen) {
        when {
            editSheetOpen -> editSheetOpen = false
            screen == Screen.TemplatePreview -> openHome()
            screen == Screen.Share -> screen = Screen.TemplatePreview
            screen == Screen.Scan -> openHome()
            screen == Screen.Records -> openHome()
        }
    }

    val view = LocalView.current
    LaunchedEffect(screen, view) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val lightScreen = screen != Screen.Scan
        controller.isAppearanceLightStatusBars = lightScreen
        controller.isAppearanceLightNavigationBars = lightScreen
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = UiTokens.SecondaryBackground) {
            when (screen) {
                Screen.TemplateList -> HomeScreen(
                    collection = collection,
                    onSelectTemplate = { id -> updateCollection(collection.select(id)) },
                    onCreateTemplate = { name ->
                        val next = StoredTemplate(
                            id = UUID.randomUUID().toString(),
                            template = TemplateState.default().withName(name),
                        )
                        updateCollection(collection.upsert(next))
                        screen = Screen.TemplatePreview
                        editSheetOpen = true
                    },
                    onRenameTemplate = { id, name ->
                        val target = collection.templates.firstOrNull { it.id == id } ?: return@HomeScreen
                        updateCollection(collection.upsert(target.copy(template = target.template.withName(name))))
                    },
                    onCopyTemplates = { ids ->
                        var nextCollection = collection
                        ids.forEach { id ->
                            val target = nextCollection.templates.firstOrNull { it.id == id } ?: return@forEach
                            nextCollection = nextCollection.upsert(
                                StoredTemplate(
                                    id = UUID.randomUUID().toString(),
                                    template = target.template.withName("${target.template.name}-副本"),
                                ),
                            )
                        }
                        updateCollection(nextCollection)
                    },
                    onDeleteTemplates = { ids ->
                        var nextCollection = collection
                        ids.forEach { id -> nextCollection = nextCollection.delete(id) }
                        updateCollection(nextCollection)
                    },
                    onOpenPreview = { screen = Screen.TemplatePreview },
                    onOpenEdit = {
                        screen = Screen.TemplatePreview
                        editSheetOpen = true
                    },
                    onOpenScan = { screen = Screen.Scan },
                    onOpenRecords = { screen = Screen.Records },
                    onOpenShare = { screen = Screen.Share },
                )

                Screen.TemplatePreview -> TemplatePreviewScreen(
                    template = template,
                    editSheetOpen = editSheetOpen,
                    onTemplateChange = { updateSelectedTemplate(it) },
                    onEditSheetChange = { editSheetOpen = it },
                    onBack = { openHome() },
                    onShare = { screen = Screen.Share },
                )

                Screen.Share -> ShareTemplateScreen(
                    template = template,
                    onBack = { screen = Screen.TemplatePreview },
                )

                Screen.Scan -> ScanScreen(
                    templateId = selected.id,
                    template = template,
                    onBack = { openHome() },
                    onOpenSetup = {
                        screen = Screen.TemplatePreview
                        editSheetOpen = true
                    },
                )

                Screen.Records -> RecordsScreen(
                    templateId = selected.id,
                    templateName = template.name,
                    onBack = { openHome() },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    collection: TemplateCollection,
    onSelectTemplate: (String) -> Unit,
    onCreateTemplate: (String) -> Unit,
    onRenameTemplate: (String, String) -> Unit,
    onCopyTemplates: (Set<String>) -> Unit,
    onDeleteTemplates: (Set<String>) -> Unit,
    onOpenPreview: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenShare: () -> Unit,
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var actionSheetTemplate by remember { mutableStateOf<StoredTemplate?>(null) }
    var createSheetOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StoredTemplate?>(null) }
    var deleteTargets by remember { mutableStateOf<Set<String>?>(null) }

    Box(Modifier.fillMaxSize().background(UiTokens.SecondaryBackground)) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                selectionMode = selectionMode,
                selectedCount = selectedIds.size,
                allSelected = selectedIds.size == collection.templates.size,
                onCancel = {
                    selectionMode = false
                    selectedIds = emptySet()
                },
                onSelectAll = {
                    selectedIds = if (selectedIds.size == collection.templates.size) {
                        emptySet()
                    } else {
                        collection.templates.map { it.id }.toSet()
                    }
                },
            )
            Column(Modifier.verticalScroll(rememberScrollState()).background(Color.White)) {
                collection.templates.forEach { stored ->
                    ExamListRow(
                        stored = stored,
                        selectionMode = selectionMode,
                        selected = stored.id in selectedIds,
                        onLongPressIcon = {
                            if (collection.templates.size >= 2) {
                                selectionMode = true
                                selectedIds = selectedIds + stored.id
                            }
                        },
                        onToggleSelected = {
                            selectedIds = if (stored.id in selectedIds) selectedIds - stored.id else selectedIds + stored.id
                        },
                        onOpenRecords = {
                            onSelectTemplate(stored.id)
                            onOpenRecords()
                        },
                        onEdit = {
                            onSelectTemplate(stored.id)
                            onOpenEdit()
                        },
                        onScan = {
                            onSelectTemplate(stored.id)
                            onOpenScan()
                        },
                        onMore = {
                            onSelectTemplate(stored.id)
                            actionSheetTemplate = stored
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(88.dp))
            }
        }

        if (!selectionMode) {
            Button(
                onClick = { createSheetOpen = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Green),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(56.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("+", color = Color.White, fontSize = 36.sp)
            }
        } else {
            MultiSelectBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                onShare = {
                    if (selectedIds.size == 1) {
                        onSelectTemplate(selectedIds.first())
                        onOpenShare()
                    }
                },
                onCopy = { onCopyTemplates(selectedIds.take(10).toSet()) },
                onDelete = { if (selectedIds.isNotEmpty()) deleteTargets = selectedIds },
            )
        }

        MoreActionSheet(
            visible = actionSheetTemplate != null,
            onDismiss = { actionSheetTemplate = null },
            onShare = {
                actionSheetTemplate = null
                onOpenShare()
            },
            onCopy = {
                actionSheetTemplate?.let { onCopyTemplates(setOf(it.id)) }
                actionSheetTemplate = null
            },
            onRename = {
                renameTarget = actionSheetTemplate
                actionSheetTemplate = null
            },
            onDelete = {
                actionSheetTemplate?.let { deleteTargets = setOf(it.id) }
                actionSheetTemplate = null
            },
        )

        TemplateNameSheet(
            visible = createSheetOpen,
            title = "创建考试",
            initialName = "",
            placeholder = "请输入考试名称，如：2024英语第一次月考",
            onDismiss = { createSheetOpen = false },
            onConfirm = { name ->
                createSheetOpen = false
                onCreateTemplate(name.ifBlank { "答题卡模板" })
            },
        )

        TemplateNameSheet(
            visible = renameTarget != null,
            title = "修改考试名称",
            initialName = renameTarget?.template?.name ?: "",
            placeholder = "请输入考试名称",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget?.let { onRenameTemplate(it.id, name) }
                renameTarget = null
            },
        )

        var displayedDeleteTargets by remember { mutableStateOf<Set<String>>(emptySet()) }
        deleteTargets?.let { displayedDeleteTargets = it }
        MiniConfirmDialog(
            visible = deleteTargets != null,
            title = "删除试卷",
            message = if (displayedDeleteTargets.size <= 1) "确认删除该试卷吗？" else "确认删除已选择的 ${displayedDeleteTargets.size} 项吗？",
            confirmText = "删除",
            destructive = true,
            onDismiss = { deleteTargets = null },
            onConfirm = {
                onDeleteTemplates(displayedDeleteTargets)
                selectedIds = selectedIds - displayedDeleteTargets
                if (selectedIds.isEmpty()) selectionMode = false
                deleteTargets = null
            },
        )
    }
}

@Composable
private fun HomeTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
) {
    MiniTopBar(
        title = if (selectionMode) "已选择 $selectedCount 项" else "考试",
        left = {
            if (selectionMode) {
                Text("取消", color = UiTokens.Red, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onCancel))
            } else {
                MiniCircleIcon("人", modifier = Modifier.size(36.dp))
            }
        },
        right = {
            if (selectionMode) {
                Text(
                    if (allSelected) "取消全选" else "全选",
                    color = UiTokens.Red,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable(onClick = onSelectAll),
                )
            }
        },
    )
}

@Composable
private fun ExamListRow(
    stored: StoredTemplate,
    selectionMode: Boolean,
    selected: Boolean,
    onLongPressIcon: () -> Unit,
    onToggleSelected: () -> Unit,
    onOpenRecords: () -> Unit,
    onEdit: () -> Unit,
    onScan: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(Color.White)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelected() else onOpenRecords() },
                onLongClick = onLongPressIcon,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectionBox(selected = selected)
        } else {
            MiniCircleIcon("卷", modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPressIcon).size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stored.template.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = UiTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "试卷号:${stablePaperNo(stored.id)} ${LocalDate.now()}",
                fontSize = 13.sp,
                color = UiTokens.TextSecondary,
                maxLines = 1,
            )
        }
        if (!selectionMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RowIcon("编", onEdit)
                RowIcon("扫", onScan)
                RowIcon("⋯", onMore)
            }
        }
    }
}

@Composable
private fun SelectionBox(selected: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) UiTokens.Red else Color.White)
            .border(1.dp, if (selected) UiTokens.Red else UiTokens.Separator, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (selected) "✓" else "", color = Color.White, fontSize = 16.sp)
    }
}

@Composable
private fun RowIcon(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, color = UiTokens.TextPrimary)
    }
}

@Composable
private fun MultiSelectBar(
    modifier: Modifier = Modifier,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(enabled = false) {}
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomAction("分享", UiTokens.TextPrimary, onShare)
        BottomAction("复制", UiTokens.TextPrimary, onCopy)
        BottomAction("删除", UiTokens.Red, onDelete)
    }
}

@Composable
private fun MoreActionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    MiniBottomFrame(visible = visible, onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomAction("分享", UiTokens.TextPrimary, onShare)
            BottomAction("复制", UiTokens.TextPrimary, onCopy)
            BottomAction("重命名", UiTokens.TextPrimary, onRename)
            BottomAction("删除", UiTokens.Red, onDelete)
        }
    }
}

@Composable
private fun BottomAction(label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (color == UiTokens.Red) 0.1f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1), fontSize = 18.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun TemplateNameSheet(
    visible: Boolean,
    title: String,
    initialName: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    LaunchedEffect(visible, initialName) {
        if (visible) name = initialName
    }
    MiniBottomFrame(visible = visible, onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { onConfirm(name) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UiTokens.MiniGreen, contentColor = Color.White),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("确定", fontSize = 16.sp)
            }
        }
    }
}

private fun stablePaperNo(id: String): String =
    id.filter { it.isDigit() }.take(7).padEnd(7, '0').ifBlank { "1730722" }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private enum class Screen {
    TemplateList,
    TemplatePreview,
    Share,
    Scan,
    Records,
}
