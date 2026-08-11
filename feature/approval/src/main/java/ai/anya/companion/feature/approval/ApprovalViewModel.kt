package ai.anya.companion.feature.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.usecase.ObserveApprovalsUseCase
import ai.anya.companion.core.domain.usecase.RespondApprovalUseCase
import ai.anya.companion.core.model.approval.ApprovalDecision
import ai.anya.companion.core.model.approval.PendingApproval
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

public data class ApprovalUiState(
    public val items: List<ApprovalListItem> = emptyList(),
)

@HiltViewModel
public class ApprovalViewModel @Inject constructor(
    observeApprovals: ObserveApprovalsUseCase,
    sessionRepository: SessionRepository,
    private val respondApproval: RespondApprovalUseCase,
) : ViewModel() {

    public val state: StateFlow<ApprovalUiState> = combine(
        observeApprovals(),
        sessionRepository.sessions,
    ) { approvals, sessions ->
        ApprovalUiState(
            items = approvals.map { approval ->
                val session = sessions.firstOrNull { it.id == approval.sessionId }
                ApprovalListItem(
                    approval = approval,
                    sessionTitle = session?.title?.takeIf { it.isNotBlank() },
                    workspaceName = session?.workspaceName?.takeIf { it.isNotBlank() },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApprovalUiState())

    public fun allowOnce(requestId: String) = decide(requestId, ApprovalDecision.AllowOnce)
    public fun deny(requestId: String) = decide(requestId, ApprovalDecision.Deny)

    private fun decide(requestId: String, decision: ApprovalDecision) {
        viewModelScope.launch { respondApproval(requestId, decision) }
    }
}
