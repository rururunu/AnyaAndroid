package ai.anya.companion.feature.approval

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.approval.ApprovalKind

@Composable
public fun ApprovalRoute(
    onOpenSession: (sessionId: String) -> Unit,
    viewModel: ApprovalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ApprovalTabContent(
        state = state,
        onOpenSession = onOpenSession,
    )
}

private fun ApprovalKind.label(): String = when (this) {
    ApprovalKind.Tool -> "权限请求"
    ApprovalKind.AskUser -> "待回答问题"
    ApprovalKind.PathPermission -> "路径权限"
}

private fun ApprovalKind.icon(): ImageVector = when (this) {
    ApprovalKind.Tool, ApprovalKind.PathPermission -> Icons.Rounded.Shield
    ApprovalKind.AskUser -> Icons.AutoMirrored.Rounded.HelpOutline
}

private fun ApprovalKind.actionHint(): String = when (this) {
    ApprovalKind.Tool, ApprovalKind.PathPermission -> "点击进入对话完成审批"
    ApprovalKind.AskUser -> "点击进入对话回答问题"
}

@Composable
public fun ApprovalTabContent(
    state: ApprovalUiState,
    onOpenSession: (sessionId: String) -> Unit,
) {
    if (state.items.isEmpty()) {
        AnyaEmptyState(
            icon = Icons.Outlined.Inbox,
            title = "收件箱为空",
            subtitle = "桌面端的审批与提问会出现在这里",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AnyaSpace.Screen,
            vertical = AnyaSpace.Lg,
        ),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        items(state.items, key = { it.approval.requestId }) { item ->
            val approval = item.approval
            AnyaSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = approval.sessionId.isNotBlank()) {
                        onOpenSession(approval.sessionId)
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                ) {
                    // Kind chip: permission request vs question.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = approval.kind.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = approval.kind.label(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Source: workspace name, or ask-anytime sessions.
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
                            text = item.workspaceName ?: "随问",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = approval.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val detail = approval.previewSummary ?: approval.toolName
                if (!detail.isNullOrBlank() && detail != approval.title) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.sessionTitle?.let { title ->
                    Text(
                        text = "对话：$title",
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
                        text = approval.kind.actionHint(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "查看对话",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
