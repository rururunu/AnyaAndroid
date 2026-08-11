package ai.anya.companion.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.usecase.RefreshWorkspaceUseCase
import ai.anya.companion.core.domain.repository.WorkspaceRepository
import ai.anya.companion.core.model.workspace.WorkspaceSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class WorkspaceUiState(
    public val snapshot: WorkspaceSnapshot? = null,
)

@HiltViewModel
public class WorkspaceViewModel @Inject constructor(
    workspaceRepository: WorkspaceRepository,
    private val refreshWorkspace: RefreshWorkspaceUseCase,
) : ViewModel() {

    public val state: StateFlow<WorkspaceUiState> = workspaceRepository.snapshot
        .map(::WorkspaceUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkspaceUiState())

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch { refreshWorkspace(sessionId = null) }
    }
}
