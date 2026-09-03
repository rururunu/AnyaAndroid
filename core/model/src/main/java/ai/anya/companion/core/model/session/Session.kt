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

/** Rewind point recorded before a user turn (desktop `Checkpoint`). */
@Serializable
public data class SessionCheckpoint(
    public val turn: Int,
    public val timeEpochMs: Long = 0L,
    public val prompt: String = "",
    public val userMessageId: String? = null,
    public val fileCount: Int = 0,
)

/** Provider-reported prompt-cache usage for one assistant message (DeepSeek). */
@Serializable
public data class MessageCacheUsage(
    public val messageId: String,
    public val inputTokens: Long = 0L,
    public val cacheReadTokens: Long = 0L,
) {
    /** cacheRead / (input + cacheRead), or null when there was no prompt at all. */
    public val hitPercent: Int?
        get() {
            val prompt = maxOf(0L, inputTokens) + maxOf(0L, cacheReadTokens)
            if (prompt <= 0L) return null
            return Math.round(maxOf(0L, cacheReadTokens) * 100.0 / prompt).toInt()
        }
}

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

/**
 * Chronological record of one assistant turn, mirroring desktop `WorkTimelineItem`:
 * reasoning / content text chunks interleaved with tool calls, so the UI can show
 * "thought → tool → thought → reply" in the order it actually happened.
 */
@Serializable
public sealed class WorkTimelineItem {
    public abstract val id: String

    @Serializable
    @SerialName("reasoning")
    public data class Reasoning(override val id: String, val content: String) : WorkTimelineItem()

    @Serializable
    @SerialName("content")
    public data class Content(override val id: String, val content: String) : WorkTimelineItem()

    @Serializable
    @SerialName("tool")
    public data class Tool(override val id: String, val toolActivityId: String) : WorkTimelineItem()
}

/** Append a text chunk, extending the trailing item when it is the same kind. */
public fun List<WorkTimelineItem>.appendTimelineText(
    delta: String,
    reasoning: Boolean,
): List<WorkTimelineItem> {
    if (delta.isEmpty()) return this
    val last = lastOrNull()
    return when {
        reasoning && last is WorkTimelineItem.Reasoning ->
            dropLast(1) + last.copy(content = last.content + delta)
        !reasoning && last is WorkTimelineItem.Content ->
            dropLast(1) + last.copy(content = last.content + delta)
        reasoning -> this + WorkTimelineItem.Reasoning(id = "reasoning-$size", content = delta)
        else -> this + WorkTimelineItem.Content(id = "content-$size", content = delta)
    }
}

/** Record a tool call once, in arrival order. */
public fun List<WorkTimelineItem>.appendTimelineTool(activityId: String): List<WorkTimelineItem> {
    if (any { it is WorkTimelineItem.Tool && it.toolActivityId == activityId }) return this
    return this + WorkTimelineItem.Tool(id = "tool-$activityId", toolActivityId = activityId)
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
    /** When the assistant turn finished (desktop `completed_at`); drives "已处理 N s". */
    public val completedAtEpochMs: Long? = null,
    /** Desktop-side token estimate for this message; null falls back to a local heuristic. */
    public val estimatedTokens: Int? = null,
    /**
     * Transient turn status from `chat.status` (`context_compacting`, `stream_retry:2:5`,
     * `analyzing_images`, `reject_empty_completion`); shown as an activity line.
     */
    public val activityStatus: String? = null,
    public val codeChanges: List<CodeChangeEntry> = emptyList(),
    public val planTasks: List<PlanTaskItem> = emptyList(),
    public val toolActivities: List<ToolActivity> = emptyList(),
    /**
     * Interleaved order of reasoning / content / tools, from the desktop history payload
     * or built locally from live gateway events. Empty means "unknown order" and the UI
     * falls back to reasoning → tools → reply.
     */
    public val workTimeline: List<WorkTimelineItem> = emptyList(),
    /** Companion-local shared files (desktop → phone). Not part of desktop history. */
    public val sharedFiles: List<ChatSharedFile> = emptyList(),
    /** Companion-local preview URLs (desktop gateway reverse-proxy). */
    public val sharedUrls: List<ChatSharedUrl> = emptyList(),
)

private const val CHARS_PER_TOKEN = 4
private const val IMAGE_TOKEN_ESTIMATE = 1000
private val DATA_IMAGE_RE = Regex("data:image/[^)]+")

/** Same heuristic as desktop `estimateTextTokens`: ~4 chars per token, 1k per inline image. */
public fun estimateTextTokens(text: String?): Int {
    if (text.isNullOrEmpty()) return 0
    val imageCount = DATA_IMAGE_RE.findAll(text).count()
    val countable = if (imageCount > 0) DATA_IMAGE_RE.replace(text, "image_placeholder") else text
    val characters = countable.codePointCount(0, countable.length)
    val textTokens = if (characters > 0) maxOf(1, characters / CHARS_PER_TOKEN) else 0
    return textTokens + imageCount * IMAGE_TOKEN_ESTIMATE
}

/** Desktop `estimateMessageTokens`: prefer the server estimate, else sum text + tool payloads. */
public fun ChatMessage.estimateTokens(): Int {
    estimatedTokens?.let { return it }
    var total = estimateTextTokens(content) + estimateTextTokens(reasoning)
    for (activity in toolActivities) {
        total += estimateTextTokens(activity.toolName)
        total += estimateTextTokens(activity.title)
        total += estimateTextTokens(activity.detail)
        total += estimateTextTokens(activity.result)
        activity.arguments?.let { total += estimateTextTokens(it.toString()) }
    }
    return total + 4
}

/** Latin compact units only (k / M), like the desktop. */
public fun formatTokenCount(tokens: Int): String {
    val n = maxOf(0, tokens)
    fun compact(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else String.format("%.1f", rounded)
    }
    return when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> compact(minOf(n / 1_000.0, 999.9)) + "k"
        else -> compact(n / 1_000_000.0) + "M"
    }
}

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

    @SerialName("image")
    Image,
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
    ChatMode.Image -> "image"
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
