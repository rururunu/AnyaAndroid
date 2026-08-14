package ai.anya.companion.core.model.inbox

import ai.anya.companion.core.model.session.SharedFileStatus
import kotlinx.serialization.Serializable

@Serializable
public enum class InboxResultKind {
    File,
    Url,
}

/**
 * A Companion-local inbox "结果" row. Survives receive/view; removed only when
 * the parent chat is deleted or the user deletes this row.
 */
@Serializable
public data class InboxResultRecord(
    public val id: String,
    public val kind: InboxResultKind,
    public val sessionId: String,
    public val sessionTitle: String? = null,
    public val workspaceName: String? = null,
    public val createdAtEpochMs: Long = 0L,
    public val name: String = "",
    public val path: String = "",
    public val mime: String = "",
    public val size: Long = 0L,
    public val fileStatus: SharedFileStatus = SharedFileStatus.Offered,
    public val localPath: String? = null,
    public val publicUrl: String = "",
    public val originUrl: String = "",
    public val urlViewed: Boolean = false,
    /** Paired desktop this row belongs to; empty = legacy (shown on every host). */
    public val deviceId: String = "",
) {
    public val needsAction: Boolean
        get() = when (kind) {
            InboxResultKind.File -> fileStatus != SharedFileStatus.Ready
            InboxResultKind.Url -> !urlViewed
        }
}
