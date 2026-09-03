package ai.anya.companion.feature.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.usecase.ObserveApprovalsUseCase
import ai.anya.companion.core.domain.usecase.RespondApprovalUseCase
import ai.anya.companion.core.model.approval.ApprovalDecision
import ai.anya.companion.core.model.approval.PendingApproval
import ai.anya.companion.core.model.inbox.InboxResultRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Inbox entry enriched with the session/workspace it belongs to. */
public data class ApprovalListItem(
    public val approval: PendingApproval,
    public val sessionTitle: String?,
    public val workspaceName: String?,
)

public data class InboxResultListItem(
    public val record: InboxResultRecord,
    public val sessionTitle: String?,
    public val workspaceName: String?,
)

public data class ApprovalUiState(
    public val items: List<ApprovalListItem> = emptyList(),
    public val results: List<InboxResultListItem> = emptyList(),
    /** Grouped results for the Results tab (sessionId → items, newest group first). */
    public val resultGroups: List<InboxResultGroup> = emptyList(),
)

public data class InboxResultGroup(
    public val sessionId: String,
    public val sessionTitle: String?,
    public val workspaceName: String?,
    public val items: List<InboxResultListItem>,
)

@HiltViewModel
public class ApprovalViewModel @Inject constructor(
    observeApprovals: ObserveApprovalsUseCase,
    private val sessionRepository: SessionRepository,
    private val respondApproval: RespondApprovalUseCase,
) : ViewModel() {

    public val state: StateFlow<ApprovalUiState> = combine(
        observeApprovals(),
        sessionRepository.sessions,
        sessionRepository.archivedSessions,
        sessionRepository.inboxResults,
    ) { approvals, sessions, archived, results ->
        val sessionById = (sessions + archived).associateBy { it.id }
        val resultItems = results.map { record ->
            val session = sessionById[record.sessionId]
            InboxResultListItem(
                record = record,
                sessionTitle = session?.title?.takeIf { it.isNotBlank() } ?: record.sessionTitle,
                workspaceName = session?.workspaceName?.takeIf { it.isNotBlank() }
                    ?: record.workspaceName,
            )
        }
        val groups = resultItems
            .groupBy { it.record.sessionId.ifBlank { "_unknown" } }
            .entries
            .map { (sessionId, items) ->
                val sorted = items.sortedByDescending { it.record.createdAtEpochMs }
                val head = sorted.first()
                InboxResultGroup(
                    sessionId = sessionId,
                    sessionTitle = head.sessionTitle,
                    workspaceName = head.workspaceName,
                    items = sorted,
                )
            }
            .sortedByDescending { group ->
                group.items.maxOfOrNull { it.record.createdAtEpochMs } ?: 0L
            }
        ApprovalUiState(
            items = approvals.map { approval ->
                val session = sessionById[approval.sessionId]
                ApprovalListItem(
                    approval = approval,
                    sessionTitle = session?.title?.takeIf { it.isNotBlank() },
                    workspaceName = session?.workspaceName?.takeIf { it.isNotBlank() },
                )
            },
            results = resultItems,
            resultGroups = groups,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApprovalUiState())

    public fun allowOnce(requestId: String) = decide(requestId, ApprovalDecision.AllowOnce)
    public fun deny(requestId: String) = decide(requestId, ApprovalDecision.Deny)

    public fun deleteResult(offerId: String) {
        sessionRepository.deleteInboxResult(offerId)
    }

    private fun decide(requestId: String, decision: ApprovalDecision) {
        viewModelScope.launch { respondApproval(requestId, decision) }
    }
}
