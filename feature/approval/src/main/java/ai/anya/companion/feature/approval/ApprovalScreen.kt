package ai.anya.companion.feature.approval

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaSegmentedControl
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.approval.ApprovalKind
import ai.anya.companion.core.model.inbox.InboxResultKind
import ai.anya.companion.core.model.session.SharedFileStatus

@Composable
public fun ApprovalRoute(
    onOpenSession: (sessionId: String) -> Unit,
    viewModel: ApprovalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ApprovalTabContent(
        state = state,
        onOpenSession = onOpenSession,
        onDeleteResult = viewModel::deleteResult,
    )
}

@Composable
public fun ApprovalTabContent(
    state: ApprovalUiState,
    onOpenSession: (sessionId: String) -> Unit,
    onDeleteResult: (String) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteItem = state.results.firstOrNull { it.record.id == pendingDelete }

    Column(modifier = Modifier.fillMaxSize()) {
        AnyaSegmentedControl(
            options = listOf(
                stringResource(R.string.inbox_tab_pending),
                stringResource(R.string.inbox_tab_results),
            ),
            selectedIndex = tab.coerceIn(0, 1),
            onSelect = { tab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen)
                .padding(top = AnyaSpace.Lg, bottom = AnyaSpace.Md),
        )
        when (tab) {
            0 -> PendingInboxList(items = state.items, onOpenSession = onOpenSession)
            else -> ResultInboxList(
                items = state.results,
                onOpenSession = onOpenSession,
                onDelete = { pendingDelete = it },
            )
        }
    }

    pendingDeleteItem?.let { item ->
        InboxResultDeleteSheet(
            title = item.record.name.ifBlank {
                if (item.record.kind == InboxResultKind.Url) {
                    item.record.publicUrl
                } else {
                    item.record.path
                }
            },
            onConfirm = {
                onDeleteResult(item.record.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun PendingInboxList(
    items: List<ApprovalListItem>,
    onOpenSession: (sessionId: String) -> Unit,
) {
    if (items.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.inbox_pending_empty_title),
            subtitle = stringResource(R.string.inbox_pending_empty_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AnyaSpace.Screen,
            vertical = AnyaSpace.Sm,
        ),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        items(items, key = { it.approval.requestId }) { item ->
            val approval = item.approval
            InboxCard(
                kindLabel = approval.kind.label(),
                kindIcon = approval.kind.icon(),
                workspaceName = item.workspaceName,
                title = approval.title,
                detail = approval.previewSummary ?: approval.toolName,
                sessionTitle = item.sessionTitle,
                hint = approval.kind.actionHint(),
                onClick = {
                    if (approval.sessionId.isNotBlank()) onOpenSession(approval.sessionId)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultInboxList(
    items: List<InboxResultListItem>,
    onOpenSession: (sessionId: String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (items.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Rounded.Description,
            title = stringResource(R.string.inbox_results_empty_title),
            subtitle = stringResource(R.string.inbox_results_empty_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AnyaSpace.Screen,
            vertical = AnyaSpace.Sm,
        ),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        items(items, key = { it.record.id }) { item ->
            val record = item.record
            InboxCard(
                kindLabel = when (record.kind) {
                    InboxResultKind.File -> stringResource(R.string.inbox_kind_file)
                    InboxResultKind.Url -> stringResource(R.string.inbox_kind_url)
                },
                kindIcon = when (record.kind) {
                    InboxResultKind.File -> Icons.Rounded.Description
                    InboxResultKind.Url -> Icons.Rounded.Language
                },
                workspaceName = item.workspaceName,
                title = record.name.ifBlank {
                    if (record.kind == InboxResultKind.Url) record.publicUrl else record.path
                },
                detail = resultStatusLabel(record),
                sessionTitle = item.sessionTitle,
                hint = resultHint(record),
                unread = record.needsAction,
                onClick = {
                    if (record.sessionId.isNotBlank()) onOpenSession(record.sessionId)
                },
                onLongClick = { onDelete(record.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxCard(
    kindLabel: String,
    kindIcon: ImageVector,
    workspaceName: String?,
    title: String,
    detail: String?,
    sessionTitle: String?,
    hint: String,
    onClick: () -> Unit,
    unread: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    AnyaSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = kindIcon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = workspaceName ?: stringResource(R.string.inbox_quick_ask),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unread) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!detail.isNullOrBlank() && detail != title) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sessionTitle?.let { session ->
            Text(
                text = stringResource(R.string.inbox_session_label, session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxResultDeleteSheet(
    title: String,
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
                    text = stringResource(R.string.inbox_delete_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.inbox_delete_body, title.ifBlank { "—" }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnyaPrimaryButton(
                text = stringResource(R.string.inbox_delete_confirm),
                onClick = {
                    haptics.confirm()
                    onConfirm()
                },
            )
            AnyaSecondaryButton(
                text = stringResource(R.string.inbox_delete_cancel),
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
private fun ApprovalKind.label(): String = when (this) {
    ApprovalKind.Tool -> stringResource(R.string.inbox_kind_tool)
    ApprovalKind.AskUser -> stringResource(R.string.inbox_kind_ask)
    ApprovalKind.PathPermission -> stringResource(R.string.inbox_kind_path)
}

private fun ApprovalKind.icon(): ImageVector = when (this) {
    ApprovalKind.Tool, ApprovalKind.PathPermission -> Icons.Rounded.Shield
    ApprovalKind.AskUser -> Icons.AutoMirrored.Rounded.HelpOutline
}

@Composable
private fun ApprovalKind.actionHint(): String = when (this) {
    ApprovalKind.Tool, ApprovalKind.PathPermission -> stringResource(R.string.inbox_hint_tool)
    ApprovalKind.AskUser -> stringResource(R.string.inbox_hint_ask)
}

@Composable
private fun resultStatusLabel(record: ai.anya.companion.core.model.inbox.InboxResultRecord): String =
    when (record.kind) {
        InboxResultKind.File -> when (record.fileStatus) {
            SharedFileStatus.Ready -> stringResource(R.string.inbox_status_file_ready)
            SharedFileStatus.Failed -> stringResource(R.string.inbox_status_file_failed)
            SharedFileStatus.Pending, SharedFileStatus.Offered ->
                stringResource(R.string.inbox_status_file_pending)
        }
        InboxResultKind.Url -> if (record.urlViewed) {
            stringResource(R.string.inbox_status_url_viewed)
        } else {
            stringResource(R.string.inbox_status_url_pending)
        }
    }

@Composable
private fun resultHint(record: ai.anya.companion.core.model.inbox.InboxResultRecord): String =
    when (record.kind) {
        InboxResultKind.File -> when (record.fileStatus) {
            SharedFileStatus.Ready -> stringResource(R.string.inbox_hint_file_ready)
            SharedFileStatus.Failed -> stringResource(R.string.inbox_hint_file_failed)
            SharedFileStatus.Pending, SharedFileStatus.Offered ->
                stringResource(R.string.inbox_hint_file_pending)
        }
        InboxResultKind.Url -> if (record.urlViewed) {
            stringResource(R.string.inbox_hint_url_viewed)
        } else {
            stringResource(R.string.inbox_hint_url_pending)
        }
    }
