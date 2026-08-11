package ai.anya.companion.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaSectionHeader
import ai.anya.companion.core.designsystem.component.AnyaStatusCard
import ai.anya.companion.core.designsystem.component.AnyaStatusTone
import ai.anya.companion.core.designsystem.component.AnyaTopBar
import ai.anya.companion.core.designsystem.theme.AnyaSpace

@Composable
public fun WorkspaceRoute(viewModel: WorkspaceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkspaceScreen(state = state)
}

@Composable
public fun WorkspaceScreen(state: WorkspaceUiState) {
    val snapshot = state.snapshot
    AnyaScreen(topBar = { AnyaTopBar(title = "工作区", subtitle = "Agent 上下文") }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AnyaSpace.Screen, vertical = AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xl),
        ) {
            AnyaSectionHeader(
                title = snapshot?.name ?: "未绑定工作区",
                subtitle = snapshot?.rootPath ?: "连接后可查看 Agent 工作区与变更。",
            )
            AnyaStatusCard(
                title = "Run 状态",
                body = snapshot?.runState ?: "unknown",
                tone = AnyaStatusTone.Info,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
                Text("变更文件", style = MaterialTheme.typography.titleMedium)
                if (snapshot?.changedFiles.isNullOrEmpty()) {
                    Text(
                        text = "暂无变更",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    snapshot?.changedFiles.orEmpty().forEach { file ->
                        Text(
                            text = file.changeType.name + "  ·  " + file.path,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
