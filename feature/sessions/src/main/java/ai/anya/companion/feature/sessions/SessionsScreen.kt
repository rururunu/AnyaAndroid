package ai.anya.companion.feature.sessions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import ai.anya.companion.core.designsystem.component.AnyaInlineLoadingMark
import ai.anya.companion.core.designsystem.component.AnyaPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaOverlayScaffold
import ai.anya.companion.core.designsystem.component.AnyaGlassSegmentedControl
import ai.anya.companion.core.designsystem.component.AnyaSegmentedControl
import ai.anya.companion.core.designsystem.component.AnyaSheetActionRow
import ai.anya.companion.core.designsystem.component.AnyaSheetCard
import ai.anya.companion.core.designsystem.component.AnyaSheetHairline
import ai.anya.companion.core.designsystem.component.AnyaSheetHeader
import com.kyant.backdrop.Backdrop
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.designsystem.theme.LocalDarkTheme
import ai.anya.companion.core.model.session.AgentRunState
import ai.anya.companion.core.model.session.ChatSessionSummary
import ai.anya.companion.core.model.session.SessionSearchHit
import ai.anya.companion.core.model.session.SessionSearchMatchKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
public fun SessionsRoute(
    onOpenSession: (String) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    SessionsTabContent(
        state = state,
        segmentIndex = segment,
        onSegmentSelect = { segment = it },
        onOpenSession = onOpenSession,
        onToggleWorkspace = viewModel::toggleWorkspace,
        onRefresh = viewModel::refresh,
        onArchiveSession = viewModel::archiveSession,
        onDeleteSession = viewModel::deleteSession,
        onArchiveWorkspace = viewModel::archiveWorkspace,
        onRenameSession = viewModel::renameSession,
        onPinWorkspace = viewModel::pinWorkspace,
        onRenameWorkspace = viewModel::renameWorkspace,
        onDismissActionError = viewModel::clearActionError,
    )
}

@Composable
public fun SessionsSegmentHeader(
    selectedIndex: Int,
    onSegmentSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedProgress: Float? = null,
    backdrop: Backdrop? = null,
) {
    val labels = listOf(
        stringResource(R.string.sessions_ask),
        stringResource(R.string.sessions_workspace),
    )
    val layout = modifier
        .fillMaxWidth()
        .padding(horizontal = AnyaSpace.Screen)
        .padding(top = AnyaSpace.Lg, bottom = AnyaSpace.Md)

    // Glass when the host has a backdrop layer to refract (main shell); the archived
    // screen has none, so it keeps the flat control.
    if (backdrop != null) {
        AnyaGlassSegmentedControl(
            backdrop = backdrop,
            options = labels,
            selectedIndex = selectedIndex.coerceIn(0, 1),
            selectedProgress = selectedProgress,
            onSelect = onSegmentSelect,
            modifier = layout,
        )
    } else {
        AnyaSegmentedControl(
            options = labels,
            selectedIndex = selectedIndex.coerceIn(0, 1),
            selectedProgress = selectedProgress,
            onSelect = onSegmentSelect,
            modifier = layout,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionsAskContent(
    state: SessionsUiState,
    onOpenSession: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onArchiveSession: ((String) -> Unit)? = null,
    onDeleteSession: ((String) -> Unit)? = null,
    onRenameSession: ((String, String) -> Unit)? = null,
    onDismissActionError: (() -> Unit)? = null,
    contentPaddingTop: Dp = 0.dp,
) {
    var pendingActions by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ChatSessionSummary?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        ActionErrorBanner(message = state.actionError, onDismiss = onDismissActionError)
        AnyaPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen),
            indicatorTopInset = contentPaddingTop,
        ) {
            QuickAskPane(
                sessions = state.quickAskSessions,
                contentPaddingTop = contentPaddingTop,
                onOpenSession = onOpenSession,
                onLongPressSession = if (
                    onArchiveSession != null || onDeleteSession != null || onRenameSession != null
                ) {
                    { session -> pendingActions = session }
                } else {
                    null
                },
            )
        }
    }
    pendingActions?.let { session ->
        SessionActionsSheet(
            session = session,
            onRename = onRenameSession?.let {
                {
                    pendingActions = null
                    renameTarget = session
                }
            },
            onArchive = onArchiveSession?.let { archive ->
                {
                    pendingActions = null
                    archive(session.id)
                }
            },
            onDelete = onDeleteSession?.let { delete ->
                {
                    pendingActions = null
                    delete(session.id)
                }
            },
            onDismiss = { pendingActions = null },
        )
    }
    renameTarget?.let { session ->
        RenameDialog(
            title = stringResource(R.string.sessions_rename),
            initialValue = session.title,
            hint = stringResource(R.string.sessions_rename_hint),
            onConfirm = { value ->
                renameTarget = null
                onRenameSession?.invoke(session.id, value)
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionsWorkspaceContent(
    state: SessionsUiState,
    onToggleWorkspace: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onArchiveSession: ((String) -> Unit)? = null,
    onDeleteSession: ((String) -> Unit)? = null,
    onNewSessionInWorkspace: ((String) -> Unit)? = null,
    onArchiveWorkspace: ((String) -> Unit)? = null,
    onRenameSession: ((String, String) -> Unit)? = null,
    onPinWorkspace: ((String, Boolean) -> Unit)? = null,
    onRenameWorkspace: ((String, String) -> Unit)? = null,
    onDismissActionError: (() -> Unit)? = null,
    contentPaddingTop: Dp = 0.dp,
) {
    var pendingActions by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var pendingWorkspace by remember { mutableStateOf<WorkspaceGroup?>(null) }
    var renameSessionTarget by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameWorkspaceTarget by remember { mutableStateOf<WorkspaceGroup?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        ActionErrorBanner(message = state.actionError, onDismiss = onDismissActionError)
        AnyaPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen),
            indicatorTopInset = contentPaddingTop,
        ) {
            WorkspacePane(
                groups = state.workspaceGroups,
                expandedIds = state.expandedWorkspaceIds,
                contentPaddingTop = contentPaddingTop,
                onToggleWorkspace = onToggleWorkspace,
                onOpenSession = onOpenSession,
                onLongPressSession = if (
                    onArchiveSession != null || onDeleteSession != null || onRenameSession != null
                ) {
                    { session -> pendingActions = session }
                } else {
                    null
                },
                onLongPressWorkspace = if (
                    onArchiveWorkspace != null || onPinWorkspace != null || onRenameWorkspace != null
                ) {
                    { group -> pendingWorkspace = group }
                } else {
                    null
                },
                onNewSessionInWorkspace = onNewSessionInWorkspace,
            )
        }
    }
    pendingActions?.let { session ->
        SessionActionsSheet(
            session = session,
            onRename = onRenameSession?.let {
                {
                    pendingActions = null
                    renameSessionTarget = session
                }
            },
            onArchive = onArchiveSession?.let { archive ->
                {
                    pendingActions = null
                    archive(session.id)
                }
            },
            onDelete = onDeleteSession?.let { delete ->
                {
                    pendingActions = null
                    delete(session.id)
                }
            },
            onDismiss = { pendingActions = null },
        )
    }
    pendingWorkspace?.let { group ->
        WorkspaceActionsSheet(
            workspaceName = group.workspace.name,
            sessionCount = group.sessions.size,
            pinned = group.workspace.pinned,
            onPin = onPinWorkspace?.let { pin ->
                {
                    pendingWorkspace = null
                    pin(group.workspace.id, !group.workspace.pinned)
                }
            },
            onRename = onRenameWorkspace?.let {
                {
                    pendingWorkspace = null
                    renameWorkspaceTarget = group
                }
            },
            onArchive = {
                pendingWorkspace = null
                onArchiveWorkspace?.invoke(group.workspace.id)
            },
            onDismiss = { pendingWorkspace = null },
        )
    }
    renameSessionTarget?.let { session ->
        RenameDialog(
            title = stringResource(R.string.sessions_rename),
            initialValue = session.title,
            hint = stringResource(R.string.sessions_rename_hint),
            onConfirm = { value ->
                renameSessionTarget = null
                onRenameSession?.invoke(session.id, value)
            },
            onDismiss = { renameSessionTarget = null },
        )
    }
    renameWorkspaceTarget?.let { group ->
        RenameDialog(
            title = stringResource(R.string.sessions_workspace_rename),
            initialValue = group.workspace.name,
            hint = stringResource(R.string.sessions_workspace_rename_hint),
            onConfirm = { value ->
                renameWorkspaceTarget = null
                onRenameWorkspace?.invoke(group.workspace.id, value)
            },
            onDismiss = { renameWorkspaceTarget = null },
        )
    }
}

@Composable
public fun SessionsTabContent(
    state: SessionsUiState,
    segmentIndex: Int,
    onSegmentSelect: (Int) -> Unit,
    onOpenSession: (String) -> Unit,
    onToggleWorkspace: (String) -> Unit,
    onRefresh: () -> Unit,
    onArchiveSession: ((String) -> Unit)? = null,
    onDeleteSession: ((String) -> Unit)? = null,
    onNewSessionInWorkspace: ((String) -> Unit)? = null,
    onArchiveWorkspace: ((String) -> Unit)? = null,
    onRenameSession: ((String, String) -> Unit)? = null,
    onPinWorkspace: ((String, Boolean) -> Unit)? = null,
    onRenameWorkspace: ((String, String) -> Unit)? = null,
    onDismissActionError: (() -> Unit)? = null,
) {
    val segment = segmentIndex.coerceIn(0, 1)
    Column(modifier = Modifier.fillMaxSize()) {
        SessionsSegmentHeader(
            selectedIndex = segment,
            onSegmentSelect = onSegmentSelect,
        )
        when (segment) {
            0 -> SessionsAskContent(
                state = state,
                onOpenSession = onOpenSession,
                onRefresh = onRefresh,
                onArchiveSession = onArchiveSession,
                onDeleteSession = onDeleteSession,
                onRenameSession = onRenameSession,
                onDismissActionError = onDismissActionError,
            )
            else -> SessionsWorkspaceContent(
                state = state,
                onToggleWorkspace = onToggleWorkspace,
                onOpenSession = onOpenSession,
                onRefresh = onRefresh,
                onArchiveSession = onArchiveSession,
                onDeleteSession = onDeleteSession,
                onNewSessionInWorkspace = onNewSessionInWorkspace,
                onArchiveWorkspace = onArchiveWorkspace,
                onRenameSession = onRenameSession,
                onPinWorkspace = onPinWorkspace,
                onRenameWorkspace = onRenameWorkspace,
                onDismissActionError = onDismissActionError,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionActionsSheet(
    session: ChatSessionSummary,
    onArchive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onRename: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = session.title.ifBlank { stringResource(R.string.sessions_delete_untitled) }
    val accent = MaterialTheme.colorScheme.primary
    // iOS action order: neutral actions first, destructive ones last.
    val actions = buildList {
        if (onRename != null) add(Triple(stringResource(R.string.sessions_rename), accent, onRename))
        if (onArchive != null) add(Triple(stringResource(R.string.sessions_archive), accent, onArchive))
        if (onDelete != null) add(Triple(stringResource(R.string.sessions_delete_confirm), AnyaColors.Danger, onDelete))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        // Apple HIG action sheet: centered title block, opaque action card, cancel card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .padding(bottom = AnyaSpace.Xl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnyaSheetHeader(
                title = stringResource(R.string.sessions_actions_title),
                subtitle = title,
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Xs))
            AnyaSheetCard {
                actions.forEachIndexed { index, (label, color, action) ->
                    AnyaSheetActionRow(
                        label = label,
                        color = color,
                        showDivider = index < actions.lastIndex,
                        onClick = action,
                    )
                }
            }
            AnyaSheetCard {
                AnyaSheetActionRow(
                    label = stringResource(R.string.sessions_delete_cancel),
                    emphasized = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceActionsSheet(
    workspaceName: String,
    sessionCount: Int,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
    pinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = MaterialTheme.colorScheme.primary
    val actions = buildList {
        if (onRename != null) add(Triple(stringResource(R.string.sessions_workspace_rename), accent, onRename))
        if (onPin != null) {
            val label = stringResource(
                if (pinned) R.string.sessions_workspace_unpin else R.string.sessions_workspace_pin,
            )
            add(Triple(label, accent, onPin))
        }
        add(Triple(stringResource(R.string.sessions_workspace_archive), accent, onArchive))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        // Apple HIG action sheet: centered title block, opaque action card, cancel card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .padding(bottom = AnyaSpace.Xl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnyaSheetHeader(
                title = stringResource(R.string.sessions_workspace_actions_title),
                subtitle = stringResource(
                    R.string.sessions_workspace_archive_body,
                    workspaceName,
                    sessionCount,
                ),
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Xs))
            AnyaSheetCard {
                actions.forEachIndexed { index, (label, color, action) ->
                    AnyaSheetActionRow(
                        label = label,
                        color = color,
                        showDivider = index < actions.lastIndex,
                        onClick = action,
                    )
                }
            }
            AnyaSheetCard {
                AnyaSheetActionRow(
                    label = stringResource(R.string.sessions_delete_cancel),
                    emphasized = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ActionErrorBanner(
    message: String?,
    onDismiss: (() -> Unit)?,
) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss?.invoke() }
            .padding(horizontal = AnyaSpace.Screen)
            .padding(bottom = AnyaSpace.Xs),
    )
}

@Composable
private fun RenameDialog(
    title: String,
    initialValue: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    // iOS alert: a narrow rounded card on a dimmed backdrop, centered content, action
    // rows separated by hairlines.
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(AnyaSpace.CardRadius),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AnyaSpace.Lg),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            RoundedCornerShape(AnyaSpace.ControlRadius),
                        )
                        .padding(horizontal = AnyaSpace.Md, vertical = AnyaSpace.Sm),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            AnyaSheetHairline()
            AnyaSheetActionRow(
                label = stringResource(R.string.sessions_rename_save),
                color = MaterialTheme.colorScheme.primary,
                showDivider = true,
                onClick = {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) onConfirm(trimmed)
                },
            )
            AnyaSheetActionRow(
                label = stringResource(R.string.sessions_delete_cancel),
                emphasized = true,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun QuickAskPane(
    sessions: List<ChatSessionSummary>,
    contentPaddingTop: Dp = 0.dp,
    onOpenSession: (String) -> Unit,
    onLongPressSession: ((ChatSessionSummary) -> Unit)? = null,
) {
    if (sessions.isEmpty()) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val emptyAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "emptyStateAlpha",
        )
        val emptyOffset by animateFloatAsState(
            targetValue = if (visible) 0f else 12f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "emptyStateOffset",
        )
        AnyaEmptyState(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = stringResource(R.string.sessions_ask_empty_title),
            subtitle = stringResource(R.string.sessions_ask_empty_subtitle),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = emptyAlpha
                    translationY = emptyOffset.dp.toPx()
                },
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Leave room for the circular new-chat FAB above the bottom nav.
        contentPadding = PaddingValues(top = contentPaddingTop, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        items(sessions, key = ChatSessionSummary::id) { session ->
            SessionCard(
                session = session,
                onClick = { onOpenSession(session.id) },
                onLongClick = onLongPressSession?.let { { it(session) } },
            )
        }
    }
}

@Composable
internal fun WorkspacePane(
    groups: List<WorkspaceGroup>,
    expandedIds: Set<String>,
    contentPaddingTop: Dp = 0.dp,
    onToggleWorkspace: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onLongPressSession: ((ChatSessionSummary) -> Unit)? = null,
    onNewSessionInWorkspace: ((String) -> Unit)? = null,
    onLongPressWorkspace: ((WorkspaceGroup) -> Unit)? = null,
) {
    if (groups.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = stringResource(R.string.sessions_workspace_none_title),
            subtitle = stringResource(R.string.sessions_workspace_none_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = contentPaddingTop, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        groups.forEach { group ->
            item(key = "ws-${group.workspace.id}") {
                WorkspaceHeader(
                    name = group.workspace.name,
                    count = group.sessions.size,
                    expanded = group.workspace.id in expandedIds,
                    onToggle = { onToggleWorkspace(group.workspace.id) },
                    onNewSession = onNewSessionInWorkspace?.let {
                        { it(group.workspace.id) }
                    },
                    onLongPress = onLongPressWorkspace?.let { { it(group) } },
                )
            }
            if (group.workspace.id in expandedIds) {
                if (group.sessions.isEmpty()) {
                    item(key = "empty-${group.workspace.id}") {
                        Text(
                            text = stringResource(R.string.sessions_workspace_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = AnyaSpace.Xxl,
                                bottom = AnyaSpace.Sm,
                            ),
                        )
                    }
                } else {
                    items(group.sessions, key = { "s-${it.id}" }) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onOpenSession(session.id) },
                            indented = true,
                            onLongClick = onLongPressSession?.let { { it(session) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewSession: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onLongPress?.let {
                    {
                        haptics.confirm()
                        it()
                    }
                },
            )
            .padding(horizontal = AnyaSpace.Md, vertical = AnyaSpace.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AnyaSpace.Sm))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .padding(horizontal = AnyaSpace.Sm, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onNewSession != null) {
            IconButton(
                onClick = {
                    haptics.buttonPress()
                    onNewSession()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.sessions_new_in_workspace),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionCard(
    session: ChatSessionSummary,
    onClick: () -> Unit,
    indented: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "sessionCardPressScale",
    )
    val cardShape = RoundedCornerShape(AnyaSpace.CardRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) AnyaSpace.Lg else 0.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(cardShape)
            .background(
                if (pressed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                shape = cardShape,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptics.buttonPress()
                        it()
                    }
                },
            )
            .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = session.title.ifBlank { stringResource(R.string.sessions_delete_untitled) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatSessionTime(session.updatedAtEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusLabel(session.runState),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(session.runState),
                    fontWeight = if (session.runState != AgentRunState.Idle) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    },
                )
            }
        }
        SessionStatusIcon(session.runState)
    }
}

@Composable
private fun SessionStatusIcon(runState: AgentRunState) {
    when (runState) {
        AgentRunState.Streaming -> {
            val transition = rememberInfiniteTransition(label = "streamingPulse")
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "streamingPulseAlpha",
            )
            StatusDot(
                color = AnyaColors.Info,
                contentDescription = statusLabel(runState),
                alpha = pulseAlpha,
            )
        }
        AgentRunState.WaitingApproval, AgentRunState.WaitingAskUser -> {
            StatusDot(
                color = AnyaColors.Warning,
                contentDescription = statusLabel(runState),
                alpha = 1f,
            )
        }
        AgentRunState.Error -> {
            Text("!", color = AnyaColors.Danger, style = MaterialTheme.typography.labelLarge)
        }
        AgentRunState.Idle -> Unit
    }
}

@Composable
private fun StatusDot(
    color: Color,
    contentDescription: String,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .size(AnyaSpace.Xxl)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AnyaSpace.Sm)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun statusLabel(runState: AgentRunState): String = when (runState) {
    AgentRunState.Idle -> stringResource(R.string.sessions_run_idle)
    AgentRunState.Streaming -> stringResource(R.string.sessions_run_streaming)
    AgentRunState.WaitingApproval -> stringResource(R.string.sessions_run_waiting_approval)
    AgentRunState.WaitingAskUser -> stringResource(R.string.sessions_run_waiting_ask)
    AgentRunState.Error -> stringResource(R.string.sessions_run_error)
}

@Composable
private fun statusColor(runState: AgentRunState): Color = when (runState) {
    AgentRunState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    AgentRunState.Streaming -> AnyaColors.Info
    AgentRunState.WaitingApproval, AgentRunState.WaitingAskUser -> AnyaColors.Warning
    AgentRunState.Error -> AnyaColors.Danger
}

private fun formatSessionTime(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val date = Date(epochMs)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> {
            val pattern = if (Locale.getDefault().language.startsWith("zh")) {
                "M月d日 HH:mm"
            } else {
                "MMM d HH:mm"
            }
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        }
        else -> SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(date)
    }
}

/**
 * Standalone search page (pushed from the main screen's search chip). The overlay
 * scaffold supplies the glass header with back + title; the panel below fills the
 * rest of the viewport. Search state is cleared when the page is popped.
 */
@Composable
public fun SessionSearchRoute(
    onBack: () -> Unit,
    onOpenSession: (sessionId: String, messageId: String?) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val haptic = rememberAnyaHaptics()
    DisposableEffect(Unit) {
        onDispose { viewModel.clearSearch() }
    }
    AnyaOverlayScaffold(
        onBack = onBack,
        title = stringResource(R.string.sessions_search),
    ) { padding ->
        SessionSearchPanel(
            state = searchState,
            onQueryChange = viewModel::onSearchQueryChange,
            onOpenSession = { sessionId, messageId ->
                haptic.linearTick()
                viewModel.clearSearch()
                onOpenSession(sessionId, messageId)
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            fillViewport = true,
        )
    }
}

@Composable
public fun SessionSearchPanel(
    state: SessionSearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenSession: (sessionId: String, messageId: String?) -> Unit,
    modifier: Modifier = Modifier,
    // True when hosted as a full page: the scaffold supplies the title, and the
    // results list fills the remaining viewport instead of capping at 480dp.
    fillViewport: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = rememberAnyaHaptics()
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AnyaSpace.Screen)
            .navigationBarsPadding()
            .padding(bottom = AnyaSpace.Md),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        if (!fillViewport) {
            AnyaSheetHeader(
                title = stringResource(R.string.sessions_search),
                subtitle = stringResource(R.string.sessions_search_help),
            )
        }
        if (fillViewport) {
            SearchQueryField(
                query = state.query,
                onQueryChange = onQueryChange,
                isSearching = state.isSearchingMessages,
                focusRequester = focusRequester,
                onSearch = { keyboard?.hide() },
            )
        } else {
            // Sheet form: the field sits in an opaque card, like the rest of the HIG sheet.
            AnyaSheetCard {
                SearchQueryField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    isSearching = state.isSearchingMessages,
                    focusRequester = focusRequester,
                    onSearch = { keyboard?.hide() },
                    modifier = Modifier.padding(AnyaSpace.Md),
                )
            }
        }

        when {
            state.query.isBlank() -> Unit
            state.results.isEmpty() && !state.isSearchingMessages -> {
                AnyaEmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.sessions_search_empty_title),
                    subtitle = stringResource(R.string.sessions_search_empty_subtitle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AnyaSpace.Xl),
                )
            }
            else -> {
                LazyColumn(
                    modifier = if (fillViewport) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                    contentPadding = PaddingValues(bottom = AnyaSpace.Sm),
                ) {
                    items(state.results, key = { "${it.matchKind}-${it.session.id}" }) { hit ->
                        SearchResultCard(
                            hit = hit,
                            onClick = {
                                haptic.linearTick()
                                onOpenSession(hit.session.id, hit.messageId)
                            },
                        )
                    }
                    if (state.isSearchingMessages) {
                        item(key = "searching") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AnyaSpace.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                            ) {
                                AnyaInlineLoadingMark(size = 14.dp)
                                Text(
                                    text = stringResource(R.string.sessions_searching_messages),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val fill = if (dark) AnyaColors.FieldFillDark else AnyaColors.FieldFillLight
    val placeholder = if (dark) AnyaColors.FieldPlaceholderDark else AnyaColors.FieldPlaceholderLight
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .background(fill)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sessions_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = placeholder,
                        )
                    }
                    inner()
                }
            },
        )
        when {
            isSearching -> AnyaInlineLoadingMark(size = 16.dp)
            query.isNotEmpty() -> {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = stringResource(R.string.sessions_search_clear),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    hit: SessionSearchHit,
    onClick: () -> Unit,
) {
    val matchLabel = when (hit.matchKind) {
        SessionSearchMatchKind.Title -> stringResource(R.string.sessions_match_title)
        SessionSearchMatchKind.Message -> stringResource(R.string.sessions_match_message)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.CardRadius))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = hit.session.title.ifBlank { stringResource(R.string.sessions_delete_untitled) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hit.matchKind == SessionSearchMatchKind.Message && hit.snippet.isNotBlank()) {
            Text(
                text = hit.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatSessionTime(hit.session.updatedAtEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = matchLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hit.session.workspaceName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
