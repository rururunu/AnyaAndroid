package ai.anya.companion.feature.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaSegmentedControl
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
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
        onDeleteSession = viewModel::deleteSession,
    )
}

@Composable
public fun SessionsSegmentHeader(
    selectedIndex: Int,
    onSegmentSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedProgress: Float? = null,
) {
    AnyaSegmentedControl(
        options = listOf(
            stringResource(R.string.sessions_ask),
            stringResource(R.string.sessions_workspace),
        ),
        selectedIndex = selectedIndex.coerceIn(0, 1),
        selectedProgress = selectedProgress,
        onSelect = onSegmentSelect,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AnyaSpace.Screen)
            .padding(top = AnyaSpace.Lg, bottom = AnyaSpace.Md),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionsAskContent(
    state: SessionsUiState,
    onOpenSession: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteSession: ((String) -> Unit)? = null,
) {
    var pendingDelete by remember { mutableStateOf<ChatSessionSummary?>(null) }
    AnyaPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnyaSpace.Screen),
    ) {
        QuickAskPane(
            sessions = state.quickAskSessions,
            onOpenSession = onOpenSession,
            onLongPressSession = onDeleteSession?.let { { session -> pendingDelete = session } },
        )
    }
    pendingDelete?.let { session ->
        SessionDeleteConfirmSheet(
            session = session,
            onConfirm = {
                pendingDelete = null
                onDeleteSession?.invoke(session.id)
            },
            onDismiss = { pendingDelete = null },
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
    onDeleteSession: ((String) -> Unit)? = null,
    onNewSessionInWorkspace: ((String) -> Unit)? = null,
) {
    var pendingDelete by remember { mutableStateOf<ChatSessionSummary?>(null) }
    AnyaPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnyaSpace.Screen),
    ) {
        WorkspacePane(
            groups = state.workspaceGroups,
            expandedIds = state.expandedWorkspaceIds,
            onToggleWorkspace = onToggleWorkspace,
            onOpenSession = onOpenSession,
            onLongPressSession = onDeleteSession?.let { { session -> pendingDelete = session } },
            onNewSessionInWorkspace = onNewSessionInWorkspace,
        )
    }
    pendingDelete?.let { session ->
        SessionDeleteConfirmSheet(
            session = session,
            onConfirm = {
                pendingDelete = null
                onDeleteSession?.invoke(session.id)
            },
            onDismiss = { pendingDelete = null },
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
    onDeleteSession: ((String) -> Unit)? = null,
    onNewSessionInWorkspace: ((String) -> Unit)? = null,
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
                onDeleteSession = onDeleteSession,
            )
            else -> SessionsWorkspaceContent(
                state = state,
                onToggleWorkspace = onToggleWorkspace,
                onOpenSession = onOpenSession,
                onRefresh = onRefresh,
                onDeleteSession = onDeleteSession,
                onNewSessionInWorkspace = onNewSessionInWorkspace,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDeleteConfirmSheet(
    session: ChatSessionSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberAnyaHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = AnyaColors.Danger,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.sessions_delete_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(
                    R.string.sessions_delete_body,
                    session.title.ifBlank { stringResource(R.string.sessions_delete_untitled) },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnyaPrimaryButton(
                text = stringResource(R.string.sessions_delete_confirm),
                onClick = {
                    haptics.confirm()
                    onConfirm()
                },
            )
            AnyaSecondaryButton(
                text = stringResource(R.string.sessions_delete_cancel),
                onClick = {
                    haptics.tick()
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Sm))
        }
    }
}

@Composable
private fun QuickAskPane(
    sessions: List<ChatSessionSummary>,
    onOpenSession: (String) -> Unit,
    onLongPressSession: ((ChatSessionSummary) -> Unit)? = null,
) {
    if (sessions.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = stringResource(R.string.sessions_ask_empty_title),
            subtitle = stringResource(R.string.sessions_ask_empty_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Leave room for the circular new-chat FAB above the bottom nav.
        contentPadding = PaddingValues(bottom = 96.dp),
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
private fun WorkspacePane(
    groups: List<WorkspaceGroup>,
    expandedIds: Set<String>,
    onToggleWorkspace: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onLongPressSession: ((ChatSessionSummary) -> Unit)? = null,
    onNewSessionInWorkspace: ((String) -> Unit)? = null,
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
        contentPadding = PaddingValues(bottom = 96.dp),
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

@Composable
private fun WorkspaceHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewSession: (() -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
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
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun SessionCard(
    session: ChatSessionSummary,
    onClick: () -> Unit,
    indented: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) AnyaSpace.Lg else 0.dp)
            .clip(RoundedCornerShape(AnyaSpace.CardRadius))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
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
                )
            }
        }
        SessionStatusIcon(session.runState)
    }
}

@Composable
private fun SessionStatusIcon(runState: AgentRunState) {
    when (runState) {
        AgentRunState.WaitingApproval, AgentRunState.WaitingAskUser -> {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = statusLabel(runState),
                modifier = Modifier.size(18.dp),
                tint = AnyaColors.Warning,
            )
        }
        AgentRunState.Streaming -> {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = statusLabel(runState),
                modifier = Modifier.size(18.dp),
                tint = AnyaColors.Info,
            )
        }
        AgentRunState.Error -> {
            Text("!", color = AnyaColors.Danger, style = MaterialTheme.typography.labelLarge)
        }
        AgentRunState.Idle -> Unit
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

@Composable
public fun SessionSearchPanel(
    state: SessionSearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenSession: (sessionId: String, messageId: String?) -> Unit,
    modifier: Modifier = Modifier,
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
        Column {
            Text(
                text = stringResource(R.string.sessions_search),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.sessions_search_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SearchQueryField(
            query = state.query,
            onQueryChange = onQueryChange,
            isSearching = state.isSearchingMessages,
            focusRequester = focusRequester,
            onSearch = { keyboard?.hide() },
        )

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
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
) {
    val dark = isSystemInDarkTheme()
    val fill = if (dark) Color(0xFF2A2A2C) else Color(0xFFF2F2F2)
    val placeholder = if (dark) Color(0xFF8E8E93) else Color(0xFF9A9A9A)
    Row(
        modifier = Modifier
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
