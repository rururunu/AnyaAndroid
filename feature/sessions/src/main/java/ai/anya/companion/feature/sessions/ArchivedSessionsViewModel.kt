package ai.anya.companion.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.usecase.ArchiveSessionUseCase
import ai.anya.companion.core.domain.usecase.ArchiveWorkspaceUseCase
import ai.anya.companion.core.domain.usecase.FindSessionsByMessageUseCase
import ai.anya.companion.core.domain.usecase.ObserveArchivedSessionsUseCase
import ai.anya.companion.core.domain.usecase.ObserveArchivedWorkspacesUseCase
import ai.anya.companion.core.domain.usecase.RefreshArchivedSessionsUseCase
import ai.anya.companion.core.domain.usecase.RefreshArchivedWorkspacesUseCase
import ai.anya.companion.core.model.session.ChatSessionSummary
import ai.anya.companion.core.model.session.SessionSearchHit
import ai.anya.companion.core.model.session.SessionSearchMatchKind
import ai.anya.companion.core.model.workspace.WorkspaceSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public data class ArchivedSessionsUiState(
    public val quickAskSessions: List<ChatSessionSummary> = emptyList(),
    public val workspaceGroups: List<WorkspaceGroup> = emptyList(),
    public val expandedWorkspaceIds: Set<String> = emptySet(),
    public val isRefreshing: Boolean = false,
)

@HiltViewModel
public class ArchivedSessionsViewModel @Inject constructor(
    observeArchived: ObserveArchivedSessionsUseCase,
    observeArchivedWorkspaces: ObserveArchivedWorkspacesUseCase,
    private val refreshArchived: RefreshArchivedSessionsUseCase,
    private val refreshArchivedWorkspaces: RefreshArchivedWorkspacesUseCase,
    private val archiveSession: ArchiveSessionUseCase,
    private val archiveWorkspace: ArchiveWorkspaceUseCase,
    private val findSessionsByMessage: FindSessionsByMessageUseCase,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private var didSeedExpanded = false

    private val _search = MutableStateFlow(SessionSearchUiState())
    public val searchState: StateFlow<SessionSearchUiState> = _search.asStateFlow()
    private var searchJob: Job? = null

    public val state: StateFlow<ArchivedSessionsUiState> = combine(
        observeArchived(),
        observeArchivedWorkspaces(),
        expanded,
        refreshing,
    ) { sessions, workspaces, expandedIds, isRefreshing ->
        val quickAsk = sessions
            .filter { it.workspaceId.isNullOrBlank() }
            .sortedByDescending { it.updatedAtEpochMs }
        val byWorkspace = sessions
            .filter { !it.workspaceId.isNullOrBlank() }
            .groupBy { it.workspaceId!! }
        val known = workspaces.associateBy { it.id }
        val groups = buildList {
            workspaces.forEach { workspace ->
                add(
                    WorkspaceGroup(
                        workspace = workspace,
                        sessions = byWorkspace[workspace.id]
                            ?.sortedByDescending { it.updatedAtEpochMs }
                            .orEmpty(),
                    ),
                )
            }
            (byWorkspace.keys - known.keys).forEach { id ->
                add(
                    WorkspaceGroup(
                        workspace = WorkspaceSummary(
                            id = id,
                            name = byWorkspace[id]?.firstOrNull()?.workspaceName ?: id,
                            archived = true,
                        ),
                        sessions = byWorkspace[id]
                            ?.sortedByDescending { it.updatedAtEpochMs }
                            .orEmpty(),
                    ),
                )
            }
        }.sortedWith(
            compareByDescending<WorkspaceGroup> { it.workspace.pinned }
                .thenBy { it.workspace.name.lowercase() },
        )
        ArchivedSessionsUiState(
            quickAskSessions = quickAsk,
            workspaceGroups = groups,
            expandedWorkspaceIds = expandedIds,
            isRefreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedSessionsUiState())

    init {
        viewModelScope.launch {
            sessionRepository.archivedWorkspaces.collect { workspaces ->
                if (!didSeedExpanded && workspaces.isNotEmpty()) {
                    didSeedExpanded = true
                    expanded.value = workspaces.take(3).map { it.id }.toSet()
                }
            }
        }
    }

    public fun toggleWorkspace(workspaceId: String) {
        expanded.update { ids ->
            if (workspaceId in ids) ids - workspaceId else ids + workspaceId
        }
    }

    public fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                refreshArchived()
                refreshArchivedWorkspaces()
            } finally {
                refreshing.value = false
            }
        }
    }

    public fun restore(sessionId: String) {
        viewModelScope.launch {
            archiveSession(sessionId, archived = false)
        }
    }

    public fun restoreWorkspace(workspaceId: String) {
        viewModelScope.launch {
            archiveWorkspace(workspaceId, archived = false)
        }
    }

    public fun onSearchQueryChange(query: String) {
        _search.update { it.copy(query = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _search.update { it.copy(results = emptyList(), isSearchingMessages = false) }
            return
        }
        searchJob = viewModelScope.launch {
            val archived = sessionRepository.archivedSessions.value
            val titleHits = archived
                .filter { it.title.contains(trimmed, ignoreCase = true) }
                .sortedByDescending { it.updatedAtEpochMs }
                .map { session ->
                    SessionSearchHit(
                        session = session,
                        matchKind = SessionSearchMatchKind.Title,
                        snippet = session.title,
                    )
                }
            _search.update {
                it.copy(results = titleHits, isSearchingMessages = true)
            }
            delay(280)
            if (!isActive) return@launch
            val archivedIds = archived.map { it.id }.toSet()
            val messageHits = findSessionsByMessage(
                query = trimmed,
                excludeSessionIds = titleHits.map { it.session.id }.toSet(),
            ).filter { it.session.id in archivedIds }
                .sortedByDescending { it.session.updatedAtEpochMs }
            if (!isActive) return@launch
            _search.update {
                it.copy(
                    results = titleHits + messageHits,
                    isSearchingMessages = false,
                )
            }
        }
    }

    public fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _search.value = SessionSearchUiState()
    }
}
