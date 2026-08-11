package ai.anya.companion.core.domain.usecase

import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.ApprovalRepository
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.repository.WorkspaceRepository
import ai.anya.companion.core.model.approval.ApprovalDecision
import ai.anya.companion.core.model.approval.PendingApproval
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.core.model.protocol.PairingPayload
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatMode
import ai.anya.companion.core.model.session.ChatModelInfo
import ai.anya.companion.core.model.session.ChatSessionSummary
import ai.anya.companion.core.model.session.PlanTaskItem
import ai.anya.companion.core.model.session.SessionCompose
import ai.anya.companion.core.model.session.SessionSearchHit
import ai.anya.companion.core.model.session.ToolApprovalMode
import ai.anya.companion.core.model.workspace.FileContent
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import ai.anya.companion.core.model.workspace.WorkspaceFilesCatalog
import ai.anya.companion.core.model.workspace.WorkspaceSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public class PairDeviceUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    public suspend operator fun invoke(payload: PairingPayload): AnyaResult<DeviceCredential> =
        connectionRepository.pair(payload)
}

public class ConnectGatewayUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    public suspend operator fun invoke(): AnyaResult<Unit> = connectionRepository.connect()
}

public class ObserveSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public operator fun invoke(): StateFlow<List<ChatSessionSummary>> = sessionRepository.sessions
}

public class RefreshSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(): AnyaResult<List<ChatSessionSummary>> =
        sessionRepository.refreshSessions()
}

public class SendChatMessageUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(
        sessionId: String?,
        message: String,
        chatMode: ChatMode? = null,
        toolApprovalMode: ToolApprovalMode? = null,
        chatModel: String? = null,
        chatModelProvider: String? = null,
    ): AnyaResult<String> =
        sessionRepository.sendMessage(sessionId, message, chatMode, toolApprovalMode, chatModel, chatModelProvider)
}

public class ObserveMessagesUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public operator fun invoke(sessionId: String): Flow<List<ChatMessage>> =
        sessionRepository.messages(sessionId)
}

public class LoadHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(sessionId: String): AnyaResult<List<ChatMessage>> =
        sessionRepository.loadHistory(sessionId)
}

public class FindSessionsByMessageUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(
        query: String,
        excludeSessionIds: Set<String> = emptySet(),
    ): List<SessionSearchHit> =
        sessionRepository.findSessionsByMessage(query, excludeSessionIds)
}

public class CancelChatMessageUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(messageId: String): AnyaResult<Unit> =
        sessionRepository.cancel(messageId)
}

public class ObserveComposeUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public operator fun invoke(sessionId: String): Flow<SessionCompose> = sessionRepository.compose(sessionId)
}

public class RefreshComposeUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(sessionId: String): AnyaResult<SessionCompose> =
        sessionRepository.refreshCompose(sessionId)
}

public class SetComposeUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(
        sessionId: String,
        chatMode: ChatMode? = null,
        toolApprovalMode: ToolApprovalMode? = null,
        chatModel: String? = null,
        chatModelProvider: String? = null,
        chatModelLabel: String? = null,
    ): AnyaResult<SessionCompose> =
        sessionRepository.setCompose(sessionId, chatMode, toolApprovalMode, chatModel, chatModelProvider, chatModelLabel)
}

public class ObserveModelsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public operator fun invoke(): StateFlow<List<ChatModelInfo>> = sessionRepository.models()
}

public class RefreshModelsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(): AnyaResult<List<ChatModelInfo>> = sessionRepository.refreshModels()
}

public class ObservePlanTasksUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public operator fun invoke(sessionId: String): Flow<List<PlanTaskItem>> = sessionRepository.planTasks(sessionId)
}

public class ApprovePlanUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke(sessionId: String): AnyaResult<Unit> =
        sessionRepository.approvePlan(sessionId)
}

public class ObserveApprovalsUseCase @Inject constructor(
    private val approvalRepository: ApprovalRepository,
) {
    public operator fun invoke(): StateFlow<List<PendingApproval>> =
        approvalRepository.pending
}

public class RespondApprovalUseCase @Inject constructor(
    private val approvalRepository: ApprovalRepository,
) {
    public suspend operator fun invoke(requestId: String, decision: ApprovalDecision): AnyaResult<Unit> =
        approvalRepository.respond(requestId, decision)
}

public class RespondAskUseCase @Inject constructor(
    private val approvalRepository: ApprovalRepository,
) {
    public suspend operator fun invoke(requestId: String, answer: String): AnyaResult<Unit> =
        approvalRepository.respondAsk(requestId, answer)
}

public class RefreshWorkspaceUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
) {
    public suspend operator fun invoke(sessionId: String?): AnyaResult<WorkspaceSnapshot> =
        workspaceRepository.refresh(sessionId)
}

public class ReadWorkspaceFileUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
) {
    public suspend operator fun invoke(path: String): AnyaResult<FileContent> =
        workspaceRepository.readFile(path)
}

public class RefreshAttachCatalogUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
) {
    public suspend operator fun invoke(sessionId: String?): AttachCatalog {
        val files = workspaceRepository.refreshFiles(sessionId)
        val skills = workspaceRepository.refreshSkills()
        val mcp = workspaceRepository.refreshMcpServers()
        return AttachCatalog(
            files = when (files) {
                is AnyaResult.Success -> files.data
                is AnyaResult.Failure -> workspaceRepository.filesCatalog.value
                    ?: WorkspaceFilesCatalog(error = errorMessage(files.error))
            },
            skills = when (skills) {
                is AnyaResult.Success -> skills.data
                is AnyaResult.Failure -> workspaceRepository.skills.value
            },
            mcpServers = when (mcp) {
                is AnyaResult.Success -> mcp.data
                is AnyaResult.Failure -> workspaceRepository.mcpServers.value
            },
            filesError = when (files) {
                is AnyaResult.Failure -> errorMessage(files.error)
                is AnyaResult.Success -> files.data.error
            },
            skillsError = (skills as? AnyaResult.Failure)?.let { errorMessage(it.error) },
            mcpError = (mcp as? AnyaResult.Failure)?.let { errorMessage(it.error) },
        )
    }

    private fun errorMessage(error: ai.anya.companion.core.common.result.AnyaError): String = when (error) {
        is ai.anya.companion.core.common.result.AnyaError.Network -> error.message
        is ai.anya.companion.core.common.result.AnyaError.Unauthorized -> error.message
        is ai.anya.companion.core.common.result.AnyaError.Protocol -> error.message
        is ai.anya.companion.core.common.result.AnyaError.NotPaired -> error.message
        is ai.anya.companion.core.common.result.AnyaError.Unknown -> error.message
    }
}

public data class AttachCatalog(
    public val files: WorkspaceFilesCatalog?,
    public val skills: List<SkillSummary>,
    public val mcpServers: List<McpServerSummary>,
    public val filesError: String? = null,
    public val skillsError: String? = null,
    public val mcpError: String? = null,
)
