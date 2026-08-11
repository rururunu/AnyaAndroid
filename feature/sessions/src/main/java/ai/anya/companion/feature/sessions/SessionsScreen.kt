package ai.anya.companion.feature.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaSegmentedControl
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
        options = listOf("随问", "工作区"),
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
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnyaSpace.Screen),
    ) {
        QuickAskPane(
            sessions = state.quickAskSessions,
            onOpenSession = onOpenSession,
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
) {
    PullToRefreshBox(
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
            )
            else -> SessionsWorkspaceContent(
                state = state,
                onToggleWorkspace = onToggleWorkspace,
                onOpenSession = onOpenSession,
                onRefresh = onRefresh,
            )
        }
    }
}

@Composable
private fun QuickAskPane(
    sessions: List<ChatSessionSummary>,
    onOpenSession: (String) -> Unit,
) {
    if (sessions.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = "暂无随问对话",
            subtitle = "桌面端新建随问后会同步到这里",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AnyaSpace.Xxl),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        items(sessions, key = ChatSessionSummary::id) { session ->
            SessionCard(session = session, onClick = { onOpenSession(session.id) })
        }
    }
}

@Composable
private fun WorkspacePane(
    groups: List<WorkspaceGroup>,
    expandedIds: Set<String>,
    onToggleWorkspace: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    if (groups.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = "暂无工作区",
            subtitle = "桌面端添加工作区后会显示在这里",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AnyaSpace.Xxl),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        groups.forEach { group ->
            item(key = "ws-${group.workspace.id}") {
                WorkspaceHeader(
                    name = group.workspace.name,
                    count = group.sessions.size,
                    expanded = group.workspace.id in expandedIds,
                    onToggle = { onToggleWorkspace(group.workspace.id) },
                )
            }
            if (group.workspace.id in expandedIds) {
                if (group.sessions.isEmpty()) {
                    item(key = "empty-${group.workspace.id}") {
                        Text(
                            text = "此工作区还没有对话",
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = AnyaSpace.Md, vertical = AnyaSpace.Md),
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
    }
}

@Composable
private fun SessionCard(
    session: ChatSessionSummary,
    onClick: () -> Unit,
    indented: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) AnyaSpace.Lg else 0.dp)
            .clip(RoundedCornerShape(AnyaSpace.CardRadius))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = session.title.ifBlank { "新对话" },
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

private fun statusLabel(runState: AgentRunState): String = when (runState) {
    AgentRunState.Idle -> "空闲"
    AgentRunState.Streaming -> "正在运行"
    AgentRunState.WaitingApproval -> "待审批"
    AgentRunState.WaitingAskUser -> "等待回答"
    AgentRunState.Error -> "出错"
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
            SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(date)
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
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 420.dp)
            .padding(horizontal = AnyaSpace.Screen)
            .padding(bottom = AnyaSpace.Xxl),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Text(
            text = "搜索对话",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("标题或消息内容") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                when {
                    state.isSearchingMessages -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    state.query.isNotEmpty() -> {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "清除",
                            )
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = RoundedCornerShape(AnyaSpace.ControlRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
            ),
        )

        when {
            state.query.isBlank() -> {
                Text(
                    text = "可按对话标题搜索，也可按对话内消息内容找到对应会话。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.results.isEmpty() && !state.isSearchingMessages -> {
                AnyaEmptyState(
                    icon = Icons.Rounded.Search,
                    title = "没有匹配的对话",
                    subtitle = "试试别的关键词",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AnyaSpace.Xl),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                    contentPadding = PaddingValues(bottom = AnyaSpace.Lg),
                ) {
                    items(state.results, key = { "${it.matchKind}-${it.session.id}" }) { hit ->
                        SearchResultCard(
                            hit = hit,
                            onClick = { onOpenSession(hit.session.id, hit.messageId) },
                        )
                    }
                    if (state.isSearchingMessages) {
                        item(key = "searching") {
                            Text(
                                text = "正在搜索消息…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = AnyaSpace.Sm),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(AnyaSpace.Sm))
    }
}

@Composable
private fun SearchResultCard(
    hit: SessionSearchHit,
    onClick: () -> Unit,
) {
    val matchLabel = when (hit.matchKind) {
        SessionSearchMatchKind.Title -> "标题"
        SessionSearchMatchKind.Message -> "消息"
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = hit.session.title.ifBlank { "新对话" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = matchLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = AnyaSpace.Sm),
            )
        }
        if (hit.matchKind == SessionSearchMatchKind.Message && hit.snippet.isNotBlank()) {
            Text(
                text = hit.snippet,
                style = MaterialTheme.typography.bodySmall,
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
