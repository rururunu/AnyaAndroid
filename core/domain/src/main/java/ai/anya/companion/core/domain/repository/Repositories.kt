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
import ai.anya.companion.core.model.settings.AppLanguage
import ai.anya.companion.core.model.update.AppUpdateInfo
import ai.anya.companion.core.model.update.UpdateCheckResult
import ai.anya.companion.core.model.update.UpdateDownloadState
import ai.anya.companion.core.model.workspace.CompanionFileOffer
import ai.anya.companion.core.model.workspace.CompanionUrlOffer
import ai.anya.companion.core.model.workspace.DownloadedWorkspaceFile
import ai.anya.companion.core.model.workspace.FileContent
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import ai.anya.companion.core.model.workspace.WorkspaceFilesCatalog
import ai.anya.companion.core.model.workspace.WorkspaceSnapshot
import ai.anya.companion.core.model.workspace.WorkspaceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
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

    /**
     * Non-suspending nudge: kick an immediate reconnect attempt when we should
     * be online but are not (app foregrounded, network switched, etc.).
     */
    public fun nudge()

    /**
     * Cold-start never completed hello. Stop retrying so the UI can leave the
     * boot splash and show the connection page.
     */
    public fun abandonUnreachableBoot()
}

public interface SessionRepository {
    public val sessions: StateFlow<List<ChatSessionSummary>>
    public val workspaces: StateFlow<List<WorkspaceSummary>>
    /** Agent-shared files pushed from desktop for download on this device. */
    public val fileOffers: SharedFlow<CompanionFileOffer>
    /** Agent-shared preview URLs (already proxied through the desktop gateway). */
    public val urlOffers: SharedFlow<CompanionUrlOffer>
    public fun messages(sessionId: String): Flow<List<ChatMessage>>
    public fun compose(sessionId: String): Flow<SessionCompose>
    public fun models(): StateFlow<List<ChatModelInfo>>
    public fun planTasks(sessionId: String): Flow<List<PlanTaskItem>>

    public suspend fun refreshSessions(): AnyaResult<List<ChatSessionSummary>>
    public suspend fun loadHistory(sessionId: String): AnyaResult<List<ChatMessage>>
    public suspend fun deleteSession(sessionId: String): AnyaResult<Unit>

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
        /** Binds a brand-new session to this workspace; ignored for existing sessions. */
        workspaceId: String? = null,
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

    /** Insert/replace a Companion-local shared-file chat card (not synced to desktop). */
    public fun upsertLocalSharedMessage(message: ChatMessage)

    /** Patch one shared-file attachment on a local card. */
    public fun patchLocalSharedFile(
        sessionId: String,
        offerId: String,
        transform: (ai.anya.companion.core.model.session.ChatSharedFile) ->
            ai.anya.companion.core.model.session.ChatSharedFile,
    )
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

    /**
     * Fetch a file over the gateway and cache it under the app's private files dir
     * (`files/shared/`). Does not write to system Downloads.
     */
    public suspend fun downloadFile(
        path: String,
        sessionId: String? = null,
        workspaceId: String? = null,
    ): AnyaResult<DownloadedWorkspaceFile>

    /**
     * Stream a local file onto the desktop (workspace `.anya/uploads` or Ask inbox)
     * so the Agent can read it via `@path`.
     */
    public suspend fun uploadLocalFile(
        sessionId: String?,
        workspaceId: String?,
        fileName: String,
        size: Long,
        mime: String?,
        input: java.io.InputStream,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): AnyaResult<ai.anya.companion.core.model.workspace.UploadedCompanionFile>

    /** Copy an already-cached shared file into the system Downloads folder. */
    public suspend fun exportCachedFileToDownloads(
        localPath: String,
        name: String,
        mime: String,
    ): AnyaResult<String>

    /** Always hits desktop for a fresh file list (desktop file tree is live). */
    public suspend fun refreshFiles(sessionId: String?, workspaceId: String? = null): AnyaResult<WorkspaceFilesCatalog>

    public suspend fun refreshSkills(): AnyaResult<List<SkillSummary>>
    public suspend fun refreshMcpServers(): AnyaResult<List<McpServerSummary>>

    /** Disk-cached skills/MCP from the last successful sync (works offline). */
    public suspend fun loadCachedAttachCatalog(): Pair<List<SkillSummary>, List<McpServerSummary>>

    /** Persist skills/MCP and remap remote icons to local file:// paths. */
    public suspend fun persistAttachCatalog(
        skills: List<SkillSummary>,
        mcpServers: List<McpServerSummary>,
    ): Pair<List<SkillSummary>, List<McpServerSummary>>
}

public interface LocaleRepository {
    public val language: StateFlow<AppLanguage>
    public fun current(): AppLanguage
    public fun setLanguage(language: AppLanguage)
}

/**
 * Sideload updates from GitHub Releases (`latest.json` or the latest `.apk` asset).
 * Same idea as the desktop Tauri updater, for a Companion that is not on Play Store.
 */
public interface UpdateRepository {
    public suspend fun check(
        currentVersion: String,
        currentVersionCode: Long,
    ): UpdateCheckResult

    public suspend fun downloadApk(
        url: String,
        destFile: java.io.File,
        expectedSize: Long? = null,
        onProgress: (written: Long, total: Long) -> Unit,
    ): AnyaResult<java.io.File>
}

/**
 * Background update checks. Starts with the process, re-checks when the app
 * returns to the foreground (throttled), and exposes the latest available APK.
 */
public interface AppUpdateMonitor {
    public val available: StateFlow<AppUpdateInfo?>
    public val checking: StateFlow<Boolean>
    public val download: StateFlow<UpdateDownloadState>
    /** Version string for nav/settings badges; null when snoozed or none. */
    public val badgeVersion: StateFlow<String?>
    /** Emitted when a newly discovered version has not been successfully notified yet. */
    public val newlyAvailable: SharedFlow<AppUpdateInfo>

    public fun start()
    public fun onForeground()
    public suspend fun checkNow(): UpdateCheckResult
    public fun startDownload()
    public suspend fun executeDownload()
    public fun launchInstaller(): Boolean
    public fun markNotified(version: String)
    public fun hasNotified(version: String): Boolean
    public fun snoozeBadge()
}
