package ai.anya.companion.core.domain.repository

import ai.anya.companion.core.common.result.AnyaResult
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
import ai.anya.companion.core.model.workspace.WorkspaceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Error,
}

public interface ConnectionRepository {
    public val connectionState: StateFlow<ConnectionState>
    public val credential: StateFlow<DeviceCredential?>

    public suspend fun pair(payload: PairingPayload): AnyaResult<DeviceCredential>
    public suspend fun connect(): AnyaResult<Unit>
    public suspend fun disconnect()
    public suspend fun clearPairing()
}

public interface SessionRepository {
    public val sessions: StateFlow<List<ChatSessionSummary>>
    public val workspaces: StateFlow<List<WorkspaceSummary>>
    public fun messages(sessionId: String): Flow<List<ChatMessage>>
    public fun compose(sessionId: String): Flow<SessionCompose>
    public fun models(): StateFlow<List<ChatModelInfo>>
    public fun planTasks(sessionId: String): Flow<List<PlanTaskItem>>

    public suspend fun refreshSessions(): AnyaResult<List<ChatSessionSummary>>
    public suspend fun loadHistory(sessionId: String): AnyaResult<List<ChatMessage>>

    /**
     * Search message bodies (and reasoning) across sessions.
     * Uses cached history when available; otherwise loads history with limited concurrency.
     */
    public suspend fun findSessionsByMessage(
        query: String,
        excludeSessionIds: Set<String> = emptySet(),
    ): List<SessionSearchHit>

    public suspend fun sendMessage(
        sessionId: String?,
        message: String,
        chatMode: ChatMode? = null,
        toolApprovalMode: ToolApprovalMode? = null,
        chatModel: String? = null,
        chatModelProvider: String? = null,
    ): AnyaResult<String>
    public suspend fun cancel(messageId: String): AnyaResult<Unit>
    public suspend fun refreshCompose(sessionId: String): AnyaResult<SessionCompose>
    public suspend fun setCompose(
        sessionId: String,
        chatMode: ChatMode? = null,
        toolApprovalMode: ToolApprovalMode? = null,
        chatModel: String? = null,
        chatModelProvider: String? = null,
        chatModelLabel: String? = null,
    ): AnyaResult<SessionCompose>
    public suspend fun refreshModels(): AnyaResult<List<ChatModelInfo>>
    public suspend fun approvePlan(sessionId: String): AnyaResult<Unit>
}

public interface ApprovalRepository {
    public val pending: StateFlow<List<PendingApproval>>

    public suspend fun respond(requestId: String, decision: ApprovalDecision): AnyaResult<Unit>
    public suspend fun respondAsk(requestId: String, answer: String): AnyaResult<Unit>
}

public interface WorkspaceRepository {
    public val snapshot: StateFlow<WorkspaceSnapshot?>
    public val filesCatalog: StateFlow<WorkspaceFilesCatalog?>
    public val skills: StateFlow<List<SkillSummary>>
    public val mcpServers: StateFlow<List<McpServerSummary>>

    public suspend fun refresh(sessionId: String?): AnyaResult<WorkspaceSnapshot>
    public suspend fun readFile(path: String): AnyaResult<FileContent>

    /** Always hits desktop for a fresh file list (desktop file tree is live). */
    public suspend fun refreshFiles(sessionId: String?, workspaceId: String? = null): AnyaResult<WorkspaceFilesCatalog>

    public suspend fun refreshSkills(): AnyaResult<List<SkillSummary>>
    public suspend fun refreshMcpServers(): AnyaResult<List<McpServerSummary>>
}
