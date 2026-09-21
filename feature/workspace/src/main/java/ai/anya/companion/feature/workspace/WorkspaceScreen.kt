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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaSectionHeader
import ai.anya.companion.core.designsystem.component.AnyaSheetCard
import ai.anya.companion.core.designsystem.component.AnyaSheetInfoRow
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
    AnyaScreen(
        topBar = {
            AnyaTopBar(
                title = stringResource(R.string.workspace_title),
                subtitle = stringResource(R.string.workspace_subtitle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AnyaSpace.Screen, vertical = AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xl),
        ) {
            AnyaSectionHeader(
                title = snapshot?.name ?: stringResource(R.string.workspace_unbound_title),
                subtitle = snapshot?.rootPath ?: stringResource(R.string.workspace_unbound_subtitle),
            )
            AnyaStatusCard(
                title = stringResource(R.string.workspace_run_state),
                body = snapshot?.runState ?: "unknown",
                tone = AnyaStatusTone.Info,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
                Text(stringResource(R.string.workspace_changed_files), style = MaterialTheme.typography.titleMedium)
                AnyaSheetCard {
                    val files = snapshot?.changedFiles.orEmpty()
                    if (files.isEmpty()) {
                        AnyaSheetInfoRow(label = stringResource(R.string.workspace_no_changes), showDivider = false)
                    } else {
                        files.forEachIndexed { index, file ->
                            AnyaSheetInfoRow(
                                label = file.path,
                                value = file.changeType.name,
                                showDivider = index < files.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}
