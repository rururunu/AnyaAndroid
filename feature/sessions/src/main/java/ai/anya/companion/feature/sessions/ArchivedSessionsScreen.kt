package ai.anya.companion.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaOverlayScaffold
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaPullToRefreshBox
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaTopBarIconChip
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatSessionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ArchivedSessionsRoute(
    onBack: () -> Unit,
    onOpenSession: (sessionId: String, messageId: String?) -> Unit = { _, _ -> },
    viewModel: ArchivedSessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    AnyaOverlayScaffold(
        onBack = onBack,
        title = stringResource(R.string.sessions_archived_title),
        actions = {
            AnyaTopBarIconChip(
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.sessions_search),
                onClick = { showSearch = true },
            )
        },
    ) { padding ->
        ArchivedSessionsContent(
            state = state,
            segmentIndex = segment,
            onSegmentSelect = { segment = it },
            onRefresh = viewModel::refresh,
            onToggleWorkspace = viewModel::toggleWorkspace,
            onRestoreSession = viewModel::restore,
            onRestoreWorkspace = viewModel::restoreWorkspace,
            onOpenSession = { id -> onOpenSession(id, null) },
            modifier = Modifier.padding(padding),
        )
    }
    if (showSearch) {
        val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val haptics = rememberAnyaHaptics()
        ModalBottomSheet(
            onDismissRequest = {
                showSearch = false
                viewModel.clearSearch()
            },
            sheetState = searchSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            SessionSearchPanel(
                state = searchState,
                onQueryChange = viewModel::onSearchQueryChange,
                onOpenSession = { sessionId, messageId ->
                    haptics.linearTick()
                    showSearch = false
                    viewModel.clearSearch()
                    onOpenSession(sessionId, messageId)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedSessionsContent(
    state: ArchivedSessionsUiState,
    segmentIndex: Int,
    onSegmentSelect: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleWorkspace: (String) -> Unit,
    onRestoreSession: (String) -> Unit,
    onRestoreWorkspace: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingSession by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingSessionObj = state.quickAskSessions.firstOrNull { it.id == pendingSession }
        ?: state.workspaceGroups.asSequence().flatMap { it.sessions.asSequence() }
            .firstOrNull { it.id == pendingSession }
    val pendingWorkspace = state.workspaceGroups.firstOrNull { it.workspace.id == pendingWorkspaceId }
    val empty = state.quickAskSessions.isEmpty() && state.workspaceGroups.isEmpty()
    Column(modifier = modifier.fillMaxSize()) {
        SessionsSegmentHeader(
            selectedIndex = segmentIndex,
            onSegmentSelect = onSegmentSelect,
        )
        if (empty && !state.isRefreshing) {
            AnyaEmptyState(
                icon = Icons.Outlined.Inventory2,
                title = stringResource(R.string.sessions_archived_empty_title),
                subtitle = stringResource(R.string.sessions_archived_empty_subtitle),
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        when (segmentIndex) {
            0 -> {
                AnyaPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AnyaSpace.Screen),
                ) {
                    if (state.quickAskSessions.isEmpty()) {
                        AnyaEmptyState(
                            icon = Icons.Outlined.Inventory2,
                            title = stringResource(R.string.sessions_archived_empty_title),
                            subtitle = stringResource(R.string.sessions_archived_empty_subtitle),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        QuickAskPane(
                            sessions = state.quickAskSessions,
                            onOpenSession = onOpenSession,
                            onLongPressSession = { pendingSession = it.id },
                        )
                    }
                }
            }
            else -> {
                AnyaPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AnyaSpace.Screen),
                ) {
                    WorkspacePane(
                        groups = state.workspaceGroups,
                        expandedIds = state.expandedWorkspaceIds,
                        onToggleWorkspace = onToggleWorkspace,
                        onOpenSession = onOpenSession,
                        onLongPressSession = { pendingSession = it.id },
                        onLongPressWorkspace = { pendingWorkspaceId = it.workspace.id },
                    )
                }
            }
        }
    }
    pendingSessionObj?.let { session ->
        RestoreSessionSheet(
            session = session,
            onRestore = {
                pendingSession = null
                onRestoreSession(session.id)
            },
            onDismiss = { pendingSession = null },
        )
    }
    pendingWorkspace?.let { group ->
        RestoreWorkspaceSheet(
            workspaceName = group.workspace.name,
            sessionCount = group.sessions.size,
            onRestore = {
                pendingWorkspaceId = null
                onRestoreWorkspace(group.workspace.id)
            },
            onDismiss = { pendingWorkspaceId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreSessionSheet(
    session: ChatSessionSummary,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberAnyaHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = session.title.ifBlank { stringResource(R.string.sessions_delete_untitled) }
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
            Text(
                text = stringResource(R.string.sessions_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AnyaPrimaryButton(
                text = stringResource(R.string.sessions_restore),
                onClick = {
                    haptics.confirm()
                    onRestore()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreWorkspaceSheet(
    workspaceName: String,
    sessionCount: Int,
    onRestore: () -> Unit,
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
            Text(
                text = stringResource(R.string.sessions_workspace_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.sessions_workspace_restore_body,
                    workspaceName,
                    sessionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnyaPrimaryButton(
                text = stringResource(R.string.sessions_workspace_restore),
                onClick = {
                    haptics.confirm()
                    onRestore()
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
