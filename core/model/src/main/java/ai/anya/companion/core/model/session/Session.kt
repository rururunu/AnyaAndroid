package ai.anya.companion.core.model.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class ChatSessionSummary(
    public val id: String,
    public val title: String,
    public val updatedAtEpochMs: Long,
    public val workspaceId: String? = null,
    public val workspaceName: String? = null,
    public val runState: AgentRunState = AgentRunState.Idle,
)

/** How a session matched a local search query. */
public enum class SessionSearchMatchKind {
    Title,
    Message,
}

public data class SessionSearchHit(
    public val session: ChatSessionSummary,
    public val matchKind: SessionSearchMatchKind,
    /** Title text, or a short message snippet around the match. */
    public val snippet: String,
    /** When matched by message content, the matching message id for deep-link scroll. */
    public val messageId: String? = null,
)

@Serializable
public enum class AgentRunState {
    @SerialName("Idle")
    Idle,
    @SerialName("Streaming")
    Streaming,
    @SerialName("WaitingApproval")
    WaitingApproval,
    @SerialName("WaitingAskUser")
    WaitingAskUser,
    @SerialName("Error")
    Error,
}

@Serializable
public data class CodeChangeEntry(
    public val id: String,
    public val path: String,
    public val added: Int = 0,
    public val removed: Int = 0,
)

@Serializable
public data class ToolPreviewPayload(
    public val path: String = "",
    public val affectedPaths: List<String> = emptyList(),
    public val unifiedDiff: String = "",
)

@Serializable
public data class ToolActivity(
    public val id: String,
    public val subagentId: String? = null,
    public val parentActivityId: String? = null,
    public val toolName: String,
    public val title: String,
    public val kind: String,
    public val detail: String? = null,
    public val arguments: JsonObject? = null,
    public val result: String? = null,
    public val preview: ToolPreviewPayload? = null,
    public val success: Boolean = true,
    public val status: String = "running",
)

@Serializable
public data class PlanTaskItem(
    public val content: String,
    public val status: String = "pending",
    public val level: Int = 0,
)

@Serializable
public data class ChatMessage(
    public val id: String,
    public val sessionId: String,
    public val role: ChatRole,
    public val content: String,
    public val reasoning: String? = null,
    public val status: MessageStatus = MessageStatus.Complete,
    public val createdAtEpochMs: Long = 0L,
    public val codeChanges: List<CodeChangeEntry> = emptyList(),
    public val planTasks: List<PlanTaskItem> = emptyList(),
    public val toolActivities: List<ToolActivity> = emptyList(),
)

@Serializable
public enum class ChatRole {
    User,
    Assistant,
    System,
}

@Serializable
public enum class MessageStatus {
    Pending,
    Streaming,
    Complete,
    Error,
    Cancelled,
}

/** Chat interaction mode. Wire values match Anya Desktop's `ChatMode` (camelCase). */
@Serializable
public enum class ChatMode {
    @SerialName("ask")
    Ask,

    @SerialName("agent")
    Agent,

    @SerialName("plan")
    Plan,
}

/** How tool calls are approved. Wire values match Anya Desktop's `ToolApprovalMode`. */
@Serializable
public enum class ToolApprovalMode {
    @SerialName("ask")
    Ask,

    @SerialName("auto")
    Auto,

    @SerialName("alwaysAllow")
    AlwaysAllow,
}

public fun ChatMode.wireValue(): String = when (this) {
    ChatMode.Ask -> "ask"
    ChatMode.Agent -> "agent"
    ChatMode.Plan -> "plan"
}

public fun ToolApprovalMode.wireValue(): String = when (this) {
    ToolApprovalMode.Ask -> "ask"
    ToolApprovalMode.Auto -> "auto"
    ToolApprovalMode.AlwaysAllow -> "alwaysAllow"
}

/** Per-session compose state mirrored from Anya Desktop (`session.compose` event). */
@Serializable
public data class SessionCompose(
    public val chatMode: ChatMode = ChatMode.Agent,
    public val toolApprovalMode: ToolApprovalMode = ToolApprovalMode.Ask,
    public val chatModel: String = "",
    public val chatModelProvider: String = "",
    public val chatModelLabel: String? = null,
) {
    public val modelDisplayName: String
        get() = chatModelLabel?.takeIf { it.isNotBlank() }
            ?: chatModel.substringAfterLast('/').substringAfterLast(':').ifBlank { "模型" }
}

@Serializable
public data class ChatModelInfo(
    public val id: String,
    public val provider: String = "",
    public val displayName: String? = null,
    public val ownedBy: String = "",
) {
    public val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: id.substringAfterLast('/').substringAfterLast(':').ifBlank { id }
}
