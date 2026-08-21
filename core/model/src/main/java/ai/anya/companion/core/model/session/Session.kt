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
public data class ChatSharedFile(
    public val offerId: String,
    public val path: String,
    public val name: String,
    public val mime: String = "application/octet-stream",
    public val size: Long = 0L,
    /** Bytes written while [status] is [SharedFileStatus.Pending]. */
    public val bytesReceived: Long = 0L,
    /** Absolute path under the app files directory (null while caching / on failure). */
    public val localPath: String? = null,
    /** content:// URI after the user saved a copy to system Downloads. */
    public val exportedUri: String? = null,
    public val status: SharedFileStatus = SharedFileStatus.Pending,
    public val error: String? = null,
    public val workspaceId: String? = null,
)

@Serializable
public data class ChatSharedUrl(
    public val offerId: String,
    public val label: String,
    public val publicUrl: String,
    public val originUrl: String = "",
)

@Serializable
public enum class SharedFileStatus {
    Offered,
    Pending,
    Ready,
    Failed,
}

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
    /** Companion-local shared files (desktop → phone). Not part of desktop history. */
    public val sharedFiles: List<ChatSharedFile> = emptyList(),
    /** Companion-local preview URLs (desktop gateway reverse-proxy). */
    public val sharedUrls: List<ChatSharedUrl> = emptyList(),
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
    public val reasoningEffort: String = "",
) {
    public val modelDisplayName: String
        get() = chatModelLabel?.takeIf { it.isNotBlank() }
            ?: chatModel.substringAfterLast('/').substringAfterLast(':').ifBlank { "模型" }
}

@Serializable
public data class ModelThinkingVariant(
    public val id: String,
    public val label: String = "",
    public val recommended: Boolean = false,
)

@Serializable
public data class ModelReasoningInfo(
    public val supported: Boolean = false,
    public val canDisable: Boolean? = null,
)

@Serializable
public data class ChatModelInfo(
    public val id: String,
    public val provider: String = "",
    public val displayName: String? = null,
    public val ownedBy: String = "",
    public val thinkingVariants: List<ModelThinkingVariant> = emptyList(),
    public val reasoning: ModelReasoningInfo? = null,
    /** Desktop picker group title for this model's provider. */
    public val providerName: String = "",
    /** Desktop preset id (`volcengine`, `kimi`, …) when this is a custom provider. */
    public val providerPresetId: String = "",
    /** Desktop favicon URL for custom provider groups. */
    public val providerFaviconUrl: String = "",
) {
    public val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: id.substringAfterLast('/').substringAfterLast(':').ifBlank { id }
}

@Serializable
public data class ContextUsageSnapshot(
    public val usageRatio: Float = 0f,
    public val estimatedTokens: Long = 0,
    public val contextWindowTokens: Long = 0,
    public val systemPromptTokens: Long = 0,
    public val toolsTokens: Long = 0,
    public val messageTokens: Long = 0,
) {
    public val percent: Int
        get() = (usageRatio.coerceIn(0f, 1f) * 100f).toInt()
}
