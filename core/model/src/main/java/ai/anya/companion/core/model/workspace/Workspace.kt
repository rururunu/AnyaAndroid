package ai.anya.companion.core.model.workspace

import kotlinx.serialization.Serializable

@Serializable
public data class WorkspaceSummary(
    public val id: String,
    public val name: String,
    public val pinned: Boolean = false,
)

@Serializable
public data class WorkspaceSnapshot(
    public val workspaceId: String? = null,
    public val name: String? = null,
    public val rootPath: String? = null,
    public val sessionId: String? = null,
    public val runState: String? = null,
    public val changedFiles: List<ChangedFile> = emptyList(),
)

@Serializable
public data class ChangedFile(
    public val path: String,
    public val changeType: ChangeType = ChangeType.Modified,
)

@Serializable
public enum class ChangeType {
    Added,
    Modified,
    Deleted,
    Renamed,
}

@Serializable
public data class FileNode(
    public val path: String,
    public val name: String,
    public val isDirectory: Boolean,
    public val children: List<FileNode> = emptyList(),
)

@Serializable
public data class FileContent(
    public val path: String,
    public val content: String,
    public val truncated: Boolean = false,
    public val languageHint: String? = null,
    public val size: Long = 0L,
)

/** A workspace file fetched over the gateway and cached on device. */
public data class DownloadedWorkspaceFile(
    public val path: String,
    public val name: String,
    public val mime: String,
    public val size: Long,
    /** Absolute filesystem path in app-private storage (not Downloads). */
    public val localPath: String,
    /** content:// FileProvider Uri suitable for ACTION_VIEW / share. */
    public val localUri: String,
)

/** A phone-local file that has been copied onto the desktop for the Agent. */
public data class UploadedCompanionFile(
    public val sessionId: String,
    public val path: String,
    public val name: String,
    public val size: Long,
)

/** Desktop Agent offered a workspace file for Companion download/preview. */
public data class CompanionFileOffer(
    public val sessionId: String,
    public val offerId: String,
    public val path: String,
    public val name: String,
    public val mime: String? = null,
    public val size: Long = 0,
    public val workspaceId: String? = null,
)

/** Desktop Agent offered a reverse-proxied local preview URL. */
public data class CompanionUrlOffer(
    public val sessionId: String,
    public val offerId: String,
    public val label: String,
    public val publicUrl: String,
    public val originUrl: String = "",
)

@Serializable
public data class WorkspaceFilesCatalog(
    public val workspaceId: String? = null,
    public val name: String? = null,
    public val rootPath: String? = null,
    public val files: List<String> = emptyList(),
    public val error: String? = null,
)

@Serializable
public data class SkillSummary(
    public val id: String,
    public val name: String = id,
    public val title: String = name,
    public val description: String = "",
    public val source: String = "user",
    public val iconUrl: String? = null,
)

@Serializable
public data class McpServerSummary(
    public val id: String,
    public val title: String = id,
    public val description: String = "",
    public val qualifiedName: String? = null,
    public val iconUrl: String? = null,
)
