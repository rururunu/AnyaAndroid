package ai.anya.companion.core.model.approval

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class AskUserOption(
    public val label: String,
    public val description: String? = null,
)

@Serializable
public data class AskUserQuestion(
    public val header: String = "",
    public val question: String = "",
    public val options: List<AskUserOption> = emptyList(),
    public val multiSelect: Boolean = false,
)

@Serializable
public data class PendingApproval(
    public val requestId: String,
    public val sessionId: String,
    public val kind: ApprovalKind,
    public val title: String,
    public val toolName: String? = null,
    public val previewSummary: String? = null,
    public val arguments: JsonObject? = null,
    public val questions: List<AskUserQuestion> = emptyList(),
    public val createdAtEpochMs: Long = 0L,
)

@Serializable
public enum class ApprovalKind {
    Tool,
    AskUser,
    PathPermission,
}

@Serializable
public enum class ApprovalDecision {
    AllowOnce,
    AllowSession,
    Deny,
}
