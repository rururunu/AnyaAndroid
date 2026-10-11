package ai.anya.companion.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.common.result.userMessage
import ai.anya.companion.core.domain.repository.ApprovalRepository
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.repository.WorkspaceExpansionRepository
import ai.anya.companion.core.domain.usecase.ArchiveSessionUseCase
import ai.anya.companion.core.domain.usecase.ArchiveWorkspaceUseCase
import ai.anya.companion.core.domain.usecase.DeleteSessionUseCase
import ai.anya.companion.core.domain.usecase.FindSessionsByMessageUseCase
import ai.anya.companion.core.domain.usecase.RefreshSessionsUseCase
import ai.anya.companion.core.model.approval.ApprovalKind
import ai.anya.companion.core.model.approval.PendingApproval
import ai.anya.companion.core.model.session.AgentRunState
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

public data class WorkspaceGroup(
    public val workspace: WorkspaceSummary,
    public val sessions: List<ChatSessionSummary>,
)

public data class SessionsUiState(
    public val connectionState: ConnectionState = ConnectionState.Disconnected,
    public val quickAskSessions: List<ChatSessionSummary> = emptyList(),
    public val workspaceGroups: List<WorkspaceGroup> = emptyList(),
    public val pendingApprovals: List<PendingApproval> = emptyList(),
    public val expandedWorkspaceIds: Set<String> = emptySet(),
    public val isRefreshing: Boolean = false,
    public val actionError: String? = null,
)

public data class SessionSearchUiState(
    public val query: String = "",
    public val results: List<SessionSearchHit> = emptyList(),
    public val isSearchingMessages: Boolean = false,
)

/** Running or waiting-on-user sessions sort above idle ones. */
private val ChatSessionSummary.isActive: Boolean
    get() = runState == AgentRunState.Streaming ||
        runState == AgentRunState.WaitingApproval ||
        runState == AgentRunState.WaitingAskUser

/** Most recent activity inside a workspace group; groups without sessions sink to the bottom. */
private val WorkspaceGroup.activityAtEpochMs: Long
    get() = sessions.maxOfOrNull { it.updatedAtEpochMs } ?: Long.MIN_VALUE

@HiltViewModel
public class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val connectionRepository: ConnectionRepository,
    approvalRepository: ApprovalRepository,
    private val refreshSessions: RefreshSessionsUseCase,
    private val findSessionsByMessage: FindSessionsByMessageUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val archiveSessionUseCase: ArchiveSessionUseCase,
    private val archiveWorkspaceUseCase: ArchiveWorkspaceUseCase,
    private val workspaceExpansionRepository: WorkspaceExpansionRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val actionError = MutableStateFlow<String?>(null)

    private val _search = MutableStateFlow(SessionSearchUiState())
    public val searchState: StateFlow<SessionSearchUiState> = _search.asStateFlow()
    private var searchJob: Job? = null

    private val catalog = combine(
        sessionRepository.sessions,
        sessionRepository.workspaces,
        approvalRepository.pending,
        connectionRepository.connectionState,
    ) { sessions, workspaces, pending, connection ->
        Catalog(sessions, workspaces, pending, connection)
    }

    public val state: StateFlow<SessionsUiState> = combine(
        catalog,
        workspaceExpansionRepository.expandedWorkspaceIds,
        refreshing,
        actionError,
    ) { cat, expandedIds, isRefreshing, error ->
        val known = cat.workspaces.associateBy { it.id }
        val pendingBySession = cat.pending.groupBy { it.sessionId }
        val enriched = cat.sessions.map { session ->
            val items = pendingBySession[session.id].orEmpty()
            val runState = when {
                items.any { it.kind == ApprovalKind.Tool } -> AgentRunState.WaitingApproval
                items.any { it.kind == ApprovalKind.AskUser } -> AgentRunState.WaitingAskUser
                else -> session.runState
            }
            session.copy(
                runState = runState,
                workspaceId = session.workspaceId?.takeIf { it in known },
            )
        }
        val quickAsk = enriched
            .filter { it.workspaceId.isNullOrBlank() }
            .sortedWith(
                compareByDescending<ChatSessionSummary> { it.isActive }
                    .thenByDescending { it.updatedAtEpochMs },
            )
        val byWorkspace = enriched
            .filter { !it.workspaceId.isNullOrBlank() }
            .groupBy { it.workspaceId!! }
        val groups = buildList {
            cat.workspaces.forEach { workspace ->
                add(
                    WorkspaceGroup(
                        workspace = workspace,
                        sessions = byWorkspace[workspace.id]
                            .orEmpty()
                            .sortedByDescending { it.updatedAtEpochMs },
                    ),
                )
            }
        }.sortedWith(
            compareByDescending<WorkspaceGroup> { it.workspace.pinned }
                .thenByDescending { it.activityAtEpochMs }
                .thenBy { it.workspace.name.lowercase() },
        )
        SessionsUiState(
            connectionState = cat.connection,
            quickAskSessions = quickAsk,
            workspaceGroups = groups,
            pendingApprovals = cat.pending,
            expandedWorkspaceIds = expandedIds,
            isRefreshing = isRefreshing,
            actionError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsUiState())

    init {
        connectionRepository.nudge()
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                if (state == ConnectionState.Connected) {
                    refresh(showIndicator = false)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(8_000)
                if (connectionRepository.connectionState.value == ConnectionState.Connected) {
                    refreshSessions()
                }
            }
        }
    }

    public fun toggleWorkspace(id: String) {
        val isExpanded = id in workspaceExpansionRepository.currentExpandedWorkspaceIds()
        workspaceExpansionRepository.setExpanded(id, expanded = !isExpanded)
    }

    public fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) refreshing.value = true
            try {
                refreshSessions()
            } finally {
                if (showIndicator) refreshing.value = false
            }
        }
    }

    public fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
        }
    }

    public fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            archiveSessionUseCase(sessionId, archived = true)
        }
    }

    public fun archiveWorkspace(workspaceId: String) {
        viewModelScope.launch {
            archiveWorkspaceUseCase(workspaceId, archived = true)
        }
    }

    public fun clearActionError() {
        actionError.value = null
    }

    public fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.renameSession(sessionId, title)) {
                is AnyaResult.Success -> actionError.value = null
                is AnyaResult.Failure -> actionError.value = result.error.userMessage
            }
        }
    }

    public fun pinWorkspace(workspaceId: String, pinned: Boolean) {
        viewModelScope.launch {
            when (val result = sessionRepository.pinWorkspace(workspaceId, pinned)) {
                is AnyaResult.Success -> actionError.value = null
                is AnyaResult.Failure -> actionError.value = result.error.userMessage
            }
        }
    }

    public fun renameWorkspace(workspaceId: String, name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.updateWorkspace(workspaceId, name)) {
                is AnyaResult.Success -> actionError.value = null
                is AnyaResult.Failure -> actionError.value = result.error.userMessage
            }
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
            val titleHits = sessionRepository.sessions.value
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
            val messageHits = findSessionsByMessage(
                query = trimmed,
                excludeSessionIds = titleHits.map { it.session.id }.toSet(),
            ).sortedByDescending { it.session.updatedAtEpochMs }
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

    private data class Catalog(
        val sessions: List<ChatSessionSummary>,
        val workspaces: List<WorkspaceSummary>,
        val pending: List<PendingApproval>,
        val connection: ConnectionState,
    )
}
