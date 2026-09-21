package ai.anya.companion.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.common.result.userMessage
import ai.anya.companion.core.domain.repository.ChatDisplayRepository
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.domain.download.FileDownloadManager
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.usecase.ApprovePlanUseCase
import ai.anya.companion.core.domain.usecase.CancelChatMessageUseCase
import ai.anya.companion.core.domain.usecase.LoadHistoryUseCase
import ai.anya.companion.core.domain.usecase.LoadMessageDetailUseCase
import ai.anya.companion.core.domain.usecase.LoadOlderHistoryUseCase
import ai.anya.companion.core.domain.usecase.ObserveApprovalsUseCase
import ai.anya.companion.core.domain.usecase.ObserveComposeUseCase
import ai.anya.companion.core.domain.usecase.ObserveMessagesUseCase
import ai.anya.companion.core.domain.usecase.ObserveModelsUseCase
import ai.anya.companion.core.domain.usecase.ObservePlanTasksUseCase
import ai.anya.companion.core.domain.usecase.RefreshAttachCatalogUseCase
import ai.anya.companion.core.domain.usecase.LoadCachedAttachCatalogUseCase
import ai.anya.companion.core.domain.usecase.RefreshComposeUseCase
import ai.anya.companion.core.domain.usecase.RefreshContextUsageUseCase
import ai.anya.companion.core.domain.usecase.RefreshModelsUseCase
import ai.anya.companion.core.domain.usecase.RespondAskUseCase
import ai.anya.companion.core.domain.usecase.SendChatMessageUseCase
import ai.anya.companion.core.domain.usecase.SetComposeUseCase
import ai.anya.companion.core.model.approval.ApprovalKind
import ai.anya.companion.core.model.approval.PendingApproval
import ai.anya.companion.core.model.session.AgentRunState
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatMode
import ai.anya.companion.core.model.session.ChatModelInfo
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.MessageCacheUsage
import ai.anya.companion.core.model.session.MessageStatus
import ai.anya.companion.core.model.session.PlanTaskItem
import ai.anya.companion.core.model.session.SessionCheckpoint
import ai.anya.companion.core.model.session.SessionCompose
import ai.anya.companion.core.model.session.ToolApprovalMode
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.ChatSharedUrl
import ai.anya.companion.core.model.session.ContextUsageSnapshot
import ai.anya.companion.core.model.session.SharedFileStatus
import ai.anya.companion.core.model.workspace.CompanionFileOffer
import ai.anya.companion.core.model.workspace.CompanionUrlOffer
import ai.anya.companion.core.model.workspace.FileNode
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import ai.anya.companion.core.model.workspace.WorkspaceFilesCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Route sentinel: open the chat screen without a session; the first send creates one. */
public const val NewChatSessionId: String = "new"

/** Page size requested when pulling older history on scroll-to-top. */
private const val HISTORY_PAGE_SIZE = 40

/** Scroll-to-top paging state for the open session. */
private data class HistoryPaging(
    val loading: Boolean = false,
    val hasMore: Boolean = true,
)

public data class ChatUiState(
    public val sessionId: String,
    public val title: String = "",
    public val workspaceName: String? = null,
    public val messages: List<ChatMessage> = emptyList(),
    public val draft: String = "",
    public val sending: Boolean = false,
    public val busy: Boolean = false,
    public val activeAssistantMessageId: String? = null,
    public val pendingAsk: PendingApproval? = null,
    public val error: String? = null,
    public val compose: SessionCompose = SessionCompose(),
    public val models: List<ChatModelInfo> = emptyList(),
    public val tasks: List<PlanTaskItem> = emptyList(),
    /** Plan messages already approved via 批准并执行 — their button must not re-fire. */
    public val planApprovedMessageIds: Set<String> = emptySet(),
    /** When opened from search, scroll to this message once history is ready. */
    public val focusMessageId: String? = null,
    /** True until the initial history fetch for this session completes. */
    public val historyLoading: Boolean = true,
    /** True while an older page of history is being pulled in on scroll-to-top. */
    public val isLoadingOlder: Boolean = false,
    /** False once a page came back short, or when the desktop cannot page history at all. */
    public val hasMoreHistory: Boolean = true,
    /** Turns whose execution process is being fetched because the user expanded them. */
    public val detailLoadingIds: Set<String> = emptySet(),
    /** Gateway link state — drives the disconnect banner and blocks actions below. */
    public val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** Rewind points keyed by the user message that started the turn. */
    public val checkpointsByUserMessage: Map<String, SessionCheckpoint> = emptyMap(),
    /** DeepSeek prompt-cache usage per assistant message. */
    public val cacheUsages: Map<String, MessageCacheUsage> = emptyMap(),
    /** True while a rewind round-trips to the desktop. */
    public val rewinding: Boolean = false,
    /** Reference images attached via "以此图编辑", sent as markdown ahead of the draft. */
    public val imageEditRefs: List<ImageEditRef> = emptyList(),
    /** Phone-side image-mode toolbar (ratio / resolution / style / count). */
    public val imageGenCompose: ai.anya.companion.core.model.image.ImageGenCompose =
        ai.anya.companion.core.model.image.ImageGenCompose(),
    /** Desktop image model / custom styles from `imageGen.options`. */
    public val imageGenOptions: ai.anya.companion.core.model.image.ImageGenOptions =
        ai.anya.companion.core.model.image.ImageGenOptions(),
    /**
     * Local thumbnails for desktop `path:` refs (album uploads / already-fetched images).
     * Not shown as shared-file cards — only used by [findLocalSharedImage] / user thumbs.
     */
    public val imagePreviews: List<ChatSharedFile> = emptyList(),
    /** Mid-turn staged queue shared with desktop. */
    public val stagedMessages: List<String> = emptyList(),
    /** Whether the model's reasoning is rendered in the transcript (Settings → Chat display). */
    public val showThinking: Boolean = false,
)

/**
 * A desktop image referenced from the composer (desktop `attachedImages` + `attachedEditSources`).
 * [previewDataUrl] is the dimmed spotlight for region edits; the desktop skips its `edit-region`
 * alt when collecting edit references, so only the full-brightness [desktopPath] reaches the API.
 */
public data class ImageEditRef(
    public val desktopPath: String,
    public val previewDataUrl: String? = null,
    public val regionPlacement: String? = null,
    /** Local content/file URI for album picks before/without a desktop cache. */
    public val localPreviewUri: String? = null,
) {
    public fun toMarkdown(): String {
        // Forward slashes keep markdown parsers happy on Windows absolute paths.
        val normalized = desktopPath.replace('\\', '/')
        val pathRef = if (normalized.startsWith("path:")) normalized else "path:$normalized"
        return buildString {
            if (previewDataUrl != null) append("![edit-region](").append(previewDataUrl).append(")\n")
            append("![image](").append(pathRef).append(')')
        }
    }
}

/** Progress / outcome of a workspace file download triggered from chat UI. */
public data class FileDownloadUiState(
    public val inProgress: Boolean = false,
    public val fileName: String? = null,
    public val message: String? = null,
    public val localUri: String? = null,
    public val mime: String? = null,
)

public data class AttachCatalogUiState(
    public val loading: Boolean = false,
    public val files: WorkspaceFilesCatalog? = null,
    public val fileTree: List<FileNode> = emptyList(),
    public val skills: List<SkillSummary> = emptyList(),
    public val mcpServers: List<McpServerSummary> = emptyList(),
    public val filesError: String? = null,
    public val skillsError: String? = null,
    public val mcpError: String? = null,
)

public data class LocalUploadItem(
    public val name: String,
    public val path: String,
    public val size: Long,
)

/** Checkpoints + cache usage + rewind flag, folded into the state after the main combine. */
private data class TurnExtras(
    val checkpointsByUserMessage: Map<String, SessionCheckpoint> = emptyMap(),
    val cacheUsages: Map<String, MessageCacheUsage> = emptyMap(),
    val rewinding: Boolean = false,
    val imageEditRefs: List<ImageEditRef> = emptyList(),
)

/** Intermediate tuple to work around [combine]'s five-flow arity limit. */
private data class ChatCoreState(
    val messages: List<ChatMessage>,
    val sessions: List<ai.anya.companion.core.model.session.ChatSessionSummary>,
    val draft: String,
    val sending: Boolean,
    val error: String?,
    val planApprovedMessageIds: Set<String> = emptySet(),
    val historyLoading: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** Null until a brand-new session gets its desktop-assigned id. */
    val sessionId: String? = null,
)

@HiltViewModel
public class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMessages: ObserveMessagesUseCase,
    observeCompose: ObserveComposeUseCase,
    observeModels: ObserveModelsUseCase,
    observePlanTasks: ObservePlanTasksUseCase,
    observeApprovals: ObserveApprovalsUseCase,
    private val sessionRepository: SessionRepository,
    private val connectionRepository: ConnectionRepository,
    private val chatDisplayRepository: ChatDisplayRepository,
    private val loadHistory: LoadHistoryUseCase,
    private val loadOlderHistory: LoadOlderHistoryUseCase,
    private val loadMessageDetail: LoadMessageDetailUseCase,
    private val sendChatMessage: SendChatMessageUseCase,
    private val cancelChatMessage: CancelChatMessageUseCase,
    private val refreshCompose: RefreshComposeUseCase,
    private val setComposeUseCase: SetComposeUseCase,
    private val refreshModels: RefreshModelsUseCase,
    private val refreshContextUsageUseCase: RefreshContextUsageUseCase,
    private val approvePlanUseCase: ApprovePlanUseCase,
    private val refreshAttachCatalog: RefreshAttachCatalogUseCase,
    private val loadCachedAttachCatalog: LoadCachedAttachCatalogUseCase,
    private val fileDownloadManager: ai.anya.companion.core.domain.download.FileDownloadManager,
    private val exportCachedFile: ai.anya.companion.core.domain.usecase.ExportCachedFileUseCase,
    private val uploadLocalFile: ai.anya.companion.core.domain.usecase.UploadLocalFileUseCase,
    private val downloadWorkspaceFile: ai.anya.companion.core.domain.usecase.DownloadWorkspaceFileUseCase,
    private val respondAsk: RespondAskUseCase,
    private val respondApproval: ai.anya.companion.core.domain.usecase.RespondApprovalUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val routeSessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val focusMessageId: String? = savedStateHandle["messageId"]

    /** Workspace to bind a brand-new session to (from the workspace-folder "+" button). */
    private val routeWorkspaceId: String? =
        savedStateHandle.get<String?>("workspaceId")?.takeUnless { it.isBlank() }

    /** Null while this is an unsent new session; set once the desktop assigns an id. */
    private val activeSessionId =
        MutableStateFlow(routeSessionId.takeUnless { it == NewChatSessionId })

    private val draft = MutableStateFlow("")
    private val sending = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val planApprovedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    private val historyLoading = MutableStateFlow(activeSessionId.value != null)
    /**
     * Starts optimistically at "more available" so the first scroll-to-top pulls a page; a short
     * page — or a desktop that cannot page history — turns it off.
     */
    private val historyPaging = MutableStateFlow(HistoryPaging())
    /** Message ids whose on-demand detail fetch is in flight. */
    private val detailLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _attachCatalog = MutableStateFlow(AttachCatalogUiState())
    public val attachCatalog: StateFlow<AttachCatalogUiState> = _attachCatalog.asStateFlow()

    private val _download = MutableStateFlow(FileDownloadUiState())
    public val download: StateFlow<FileDownloadUiState> = _download.asStateFlow()

    private val _localUploads = MutableStateFlow<List<LocalUploadItem>>(emptyList())
    public val localUploads: StateFlow<List<LocalUploadItem>> = _localUploads.asStateFlow()

    private val _contextUsage = MutableStateFlow(ContextUsageSnapshot())
    public val contextUsage: StateFlow<ContextUsageSnapshot> = _contextUsage.asStateFlow()

    /** Compose choices made before the first message creates the session. */
    private val draftCompose = MutableStateFlow(SessionCompose())

    private val pendingAskForSession = combine(
        observeApprovals(),
        activeSessionId,
    ) { list, sid ->
        if (sid == null) {
            null
        } else {
            list.firstOrNull {
                it.sessionId == sid &&
                    (
                        it.kind == ApprovalKind.AskUser ||
                            it.kind == ApprovalKind.Tool ||
                            it.kind == ApprovalKind.PathPermission
                        )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionMessages = activeSessionId.flatMapLatest { sid ->
        if (sid == null) flowOf(emptyList()) else observeMessages(sid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val composeFlow = activeSessionId.flatMapLatest { sid ->
        if (sid == null) draftCompose else observeCompose(sid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val planTasksFlow = activeSessionId.flatMapLatest { sid ->
        if (sid == null) flowOf(emptyList()) else observePlanTasks(sid)
    }

    private val rewinding = MutableStateFlow(false)
    private val imageEditRefs = MutableStateFlow<List<ImageEditRef>>(emptyList())
    /** Desktop absolute path → Ready local cache (album refs, no inbox card). */
    private val imagePreviewCache = MutableStateFlow<Map<String, ChatSharedFile>>(emptyMap())
    /** Paths already handed to [ensureImagePreview] so user thumbs don't spam fetches. */
    private val autoFetchedImagePaths =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val turnExtras = activeSessionId.flatMapLatest { sid ->
        if (sid == null) {
            imageEditRefs.map { TurnExtras(imageEditRefs = it) }
        } else {
            combine(
                sessionRepository.checkpoints(sid),
                sessionRepository.cacheUsages(sid),
                rewinding,
                imageEditRefs,
            ) { checkpoints, usages, busy, refs ->
                TurnExtras(
                    checkpointsByUserMessage = checkpoints
                        .filter { !it.userMessageId.isNullOrBlank() }
                        .associateBy { it.userMessageId!! },
                    cacheUsages = usages,
                    rewinding = busy,
                    imageEditRefs = refs,
                )
            }
        }
    }

    private val core: StateFlow<ChatCoreState> = combine(
        sessionMessages,
        sessionRepository.sessions,
        draft,
        sending,
        error,
    ) { messages, sessions, draftText, isSending, err ->
        ChatCoreState(messages, sessions, draftText, isSending, err)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatCoreState(emptyList(), emptyList(), "", false, null),
    )

    // Approval flags + history-loading + link state live outside the five-flow
    // combine limit; fold them in separately.
    private val coreWithApprovals: StateFlow<ChatCoreState> = combine(
        core,
        planApprovedMessageIds,
        historyLoading,
        connectionRepository.connectionState,
        activeSessionId,
    ) { c, ids, loading, connection, sid ->
        c.copy(
            planApprovedMessageIds = ids,
            historyLoading = loading,
            connectionState = connection,
            sessionId = sid,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatCoreState(emptyList(), emptyList(), "", false, null),
    )

    private val baseState: StateFlow<ChatUiState> = combine(
        coreWithApprovals,
        composeFlow,
        observeModels(),
        planTasksFlow,
        pendingAskForSession,
    ) { c, compose, models, tasks, pendingAsk ->
        val session = c.sessions.firstOrNull { it.id == c.sessionId }
        val activeAssistantId = c.messages
            .asReversed()
            .firstOrNull {
                it.role == ChatRole.Assistant &&
                    (it.status == MessageStatus.Pending || it.status == MessageStatus.Streaming)
            }
            ?.id
        val waitingAsk = pendingAsk != null
        val busy = c.sending ||
            activeAssistantId != null ||
            session?.runState == AgentRunState.Streaming ||
            waitingAsk ||
            session?.runState == AgentRunState.WaitingApproval
        ChatUiState(
            sessionId = c.sessionId.orEmpty(),
            title = session?.title.orEmpty(),
            workspaceName = session?.workspaceName,
            messages = c.messages,
            draft = c.draft,
            sending = c.sending,
            busy = busy,
            activeAssistantMessageId = activeAssistantId,
            pendingAsk = pendingAsk,
            error = c.error,
            compose = resolveComposeLabel(compose, models),
            models = models,
            tasks = tasks,
            planApprovedMessageIds = c.planApprovedMessageIds,
            focusMessageId = focusMessageId,
            historyLoading = c.historyLoading,
            connectionState = c.connectionState,
        )
    }.combine(historyPaging) { state, paging ->
        state.copy(isLoadingOlder = paging.loading, hasMoreHistory = paging.hasMore)
    }.combine(detailLoadingIds) { state, ids ->
        state.copy(detailLoadingIds = ids)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatUiState(
            sessionId = activeSessionId.value.orEmpty(),
            focusMessageId = focusMessageId,
            historyLoading = activeSessionId.value != null,
        ),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stagedFlow = activeSessionId.flatMapLatest { sid ->
        if (sid == null) flowOf(emptyList()) else sessionRepository.stagedMessages(sid)
    }

    public val state: StateFlow<ChatUiState> = combine(
        baseState,
        turnExtras,
        sessionRepository.imageGenCompose,
        sessionRepository.imageGenOptions,
        imagePreviewCache,
    ) { base, extras, imageCompose, imageOptions, previews ->
        base.copy(
            checkpointsByUserMessage = extras.checkpointsByUserMessage,
            cacheUsages = extras.cacheUsages,
            rewinding = extras.rewinding,
            imageEditRefs = extras.imageEditRefs,
            imageGenCompose = imageCompose,
            imageGenOptions = imageOptions,
            imagePreviews = previews.values.toList(),
        )
    }.let { coreFlow ->
        combine(coreFlow, stagedFlow, chatDisplayRepository.showThinking) { base, staged, showThinking ->
            base.copy(
                stagedMessages = staged,
                showThinking = showThinking,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatUiState(
            sessionId = activeSessionId.value.orEmpty(),
            focusMessageId = focusMessageId,
            historyLoading = activeSessionId.value != null,
        ),
    )

    /**
     * Pulls the previous page of transcript when the user scrolls back to the very top.
     *
     * The repository answers null once it learns the desktop cannot page history; that turns the
     * affordance off instead of retrying the probe on every scroll.
     */
    public fun loadOlderMessages() {
        val sid = activeSessionId.value ?: return
        val paging = historyPaging.value
        if (paging.loading || !paging.hasMore) return
        val oldest = state.value.messages.firstOrNull() ?: return
        historyPaging.value = paging.copy(loading = true)
        viewModelScope.launch {
            val hasMore = when (val loaded = loadOlderHistory(sid, oldest.id, HISTORY_PAGE_SIZE)) {
                is AnyaResult.Success -> loaded.data
                is AnyaResult.Failure, null -> false
            }
            historyPaging.value = HistoryPaging(loading = false, hasMore = hasMore)
        }
    }

    /**
     * Fetches a turn's execution process the first time the user expands it.
     *
     * Light history leaves reasoning / work timeline / tool activities out; the desktop sends them
     * on demand and the repository merges the result back into the projection.
     */
    public fun ensureMessageDetail(messageId: String) {
        val sid = activeSessionId.value ?: return
        if (messageId in detailLoadingIds.value) return
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (!message.detailOmitted) return
        detailLoadingIds.value = detailLoadingIds.value + messageId
        viewModelScope.launch {
            try {
                loadMessageDetail(sid, listOf(messageId))
            } finally {
                detailLoadingIds.value = detailLoadingIds.value - messageId
            }
        }
    }

    /**
     * Roll the desktop session back to the checkpoint before [userMessageId] (code + conversation),
     * then leave that prompt in the composer so it can be edited and re-sent — same as the desktop.
     */
    public fun rewindToUserMessage(userMessageId: String) {
        val sid = activeSessionId.value ?: return
        if (!requireConnectedOrWarn()) return
        val checkpoint = state.value.checkpointsByUserMessage[userMessageId] ?: return
        val promptText = state.value.messages.firstOrNull { it.id == userMessageId }?.content.orEmpty()
        rewinding.value = true
        viewModelScope.launch {
            when (val result = sessionRepository.rewindSession(sid, checkpoint.turn)) {
                is AnyaResult.Success -> if (promptText.isNotBlank()) draft.value = promptText
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
            rewinding.value = false
        }
    }

    /**
     * "以此图编辑": reference a desktop image (by its PC path) in Image mode. [previewDataUrl]
     * is the dimmed spotlight for region edits; the desktop skips `edit-region` alts when it
     * collects edit references, so only the full-brightness original reaches the Images API.
     */
    public fun startImageEdit(
        desktopPath: String,
        previewDataUrl: String?,
        regionPlacement: String?,
    ) {
        if (state.value.compose.chatMode != ChatMode.Image) setChatMode(ChatMode.Image)
        imageEditRefs.value = listOf(
            ImageEditRef(
                desktopPath = desktopPath,
                previewDataUrl = previewDataUrl,
                regionPlacement = regionPlacement,
            ),
        )
        // Same draft prompts as the desktop; the user completes the sentence.
        draft.value = if (regionPlacement != null) {
            "保持参考图原有亮度、色彩和未提及区域不变。只改我涂抹标出的${regionPlacement}区域："
        } else {
            "保持参考图原有亮度和整体构图。修改："
        }
    }

    public fun clearImageEditRefs() {
        imageEditRefs.value = emptyList()
    }

    public fun removeImageEditRef(ref: ImageEditRef) {
        imageEditRefs.update { list -> list.filterNot { it == ref } }
    }

    public fun updateImageGenCompose(
        ratio: String? = null,
        resolution: String? = null,
        styleId: String? = null,
        count: Int? = null,
    ) {
        viewModelScope.launch {
            val current = state.value.imageGenCompose
            sessionRepository.saveImageGenCompose(
                current.copy(
                    ratio = ratio ?: current.ratio,
                    resolution = resolution ?: current.resolution,
                    styleId = styleId ?: current.styleId,
                    count = count ?: current.count,
                ),
            )
        }
    }

    public fun refreshImageGenOptions() {
        if (connectionRepository.connectionState.value != ConnectionState.Connected) return
        viewModelScope.launch { sessionRepository.refreshImageGenOptions() }
    }

    public fun setImageGenModel(provider: String, model: String) {
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            when (val result = sessionRepository.setImageGenModel(provider, model)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
        }
    }

    /**
     * Upload album images to the desktop and attach them as `path:` references
     * (same markdown shape as "以此图编辑").
     */
    public fun uploadAlbumAsRefs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!requireConnectedOrWarn()) return
        if (state.value.compose.chatMode != ChatMode.Image) setChatMode(ChatMode.Image)
        viewModelScope.launch {
            for (uri in uris) {
                uploadOneUriAsRef(uri)
            }
        }
    }

    init {
        viewModelScope.launch {
            val cached = loadCachedAttachCatalog()
            if (cached.skills.isNotEmpty() || cached.mcpServers.isNotEmpty()) {
                _attachCatalog.update {
                    it.copy(
                        skills = cached.skills,
                        mcpServers = cached.mcpServers,
                    )
                }
            }
        }
        viewModelScope.launch {
            connectionRepository.connectionState
                .map { it == ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected ->
                    val sid = activeSessionId.value
                    if (!connected) {
                        // Don't block the transcript behind a spinner when offline —
                        // cached messages (if any) should remain visible.
                        historyLoading.value = false
                        return@collect
                    }
                    if (sid != null) {
                        historyLoading.value = true
                        try {
                            loadHistory(sid)
                        } finally {
                            historyLoading.value = false
                        }
                        refreshCompose(sid)
                        sessionRepository.refreshStaged(sid)
                    } else {
                        historyLoading.value = false
                    }
                    refreshModels()
                    refreshAttachCatalog()
                    sessionRepository.refreshImageGenOptions()
                }
        }
        // Opening a chat is a strong signal the user wants to be online *now* —
        // don't just wait for the background reconnect loop (which backs off up
        // to 8s between attempts, and can stall entirely while the process was
        // suspended in the background) to eventually get around to it.
        connectionRepository.nudge()

        // Agent shared a file from desktop — show a card; fetch bytes only on tap.
        viewModelScope.launch {
            sessionRepository.fileOffers.collect { offer ->
                ingestSharedOffer(offer, autoFetch = false)
            }
        }
        viewModelScope.launch {
            sessionRepository.urlOffers.collect { offer ->
                ingestUrlOffer(offer)
            }
        }
    }

    private suspend fun ingestSharedOffer(offer: CompanionFileOffer, autoFetch: Boolean) {
        val offerId = offer.offerId.ifBlank { UUID.randomUUID().toString() }
        val messageId = "shared-$offerId"
        val mime = offer.mime?.takeIf { it.isNotBlank() } ?: guessMime(offer.name)
        val pending = ChatSharedFile(
            offerId = offerId,
            path = offer.path,
            name = offer.name,
            mime = mime,
            size = offer.size,
            status = if (autoFetch) SharedFileStatus.Pending else SharedFileStatus.Offered,
            workspaceId = offer.workspaceId,
        )
        sessionRepository.upsertLocalSharedMessage(
            ChatMessage(
                id = messageId,
                sessionId = offer.sessionId,
                role = ChatRole.System,
                content = "",
                status = MessageStatus.Complete,
                createdAtEpochMs = System.currentTimeMillis(),
                sharedFiles = listOf(pending),
            ),
        )
        if (autoFetch) {
            fetchSharedFileInternal(offer.sessionId, offerId, offer.path, offer.workspaceId, offer.name)
        }
    }

    private suspend fun ingestUrlOffer(offer: CompanionUrlOffer) {
        val offerId = offer.offerId.ifBlank { UUID.randomUUID().toString() }
        sessionRepository.upsertLocalSharedMessage(
            ChatMessage(
                id = "shared-url-$offerId",
                sessionId = offer.sessionId,
                role = ChatRole.System,
                content = "",
                status = MessageStatus.Complete,
                createdAtEpochMs = System.currentTimeMillis(),
                sharedUrls = listOf(
                    ChatSharedUrl(
                        offerId = offerId,
                        label = offer.label.ifBlank { "Preview" },
                        publicUrl = offer.publicUrl,
                        originUrl = offer.originUrl,
                    ),
                ),
            ),
        )
    }

    public fun markInboxUrlViewed(offerId: String) {
        sessionRepository.markInboxUrlViewed(offerId)
    }

    public fun fetchSharedFile(offerId: String) {
        val sid = activeSessionId.value ?: return
        if (!requireConnectedOrWarn()) return
        val file = state.value.messages
            .asSequence()
            .flatMap { it.sharedFiles.asSequence() }
            .firstOrNull { it.offerId == offerId }
            ?: return
        if (file.status == SharedFileStatus.Ready || file.status == SharedFileStatus.Pending) return
        sessionRepository.patchLocalSharedFile(sid, offerId) { current ->
            current.copy(status = SharedFileStatus.Pending, error = null)
        }
        fetchSharedFileInternal(sid, offerId, file.path, file.workspaceId, file.name)
    }

    private fun fetchSharedFileInternal(
        sessionId: String,
        offerId: String,
        path: String,
        workspaceId: String?,
        name: String,
    ) {
        fileDownloadManager.download(sessionId, offerId, path, workspaceId, name)
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    /** Called from the disconnect banner's "重连" action. */
    public fun retryConnection() {
        connectionRepository.nudge()
    }

    /**
     * Gate for anything that talks to the desktop (send/approve/answer/cancel).
     * When offline, fail fast with a clear message and kick a reconnect attempt
     * instead of letting the request go out, time out, or fail with a raw
     * "Gateway is not connected" exception string.
     */
    private fun requireConnectedOrWarn(): Boolean {
        if (connectionRepository.connectionState.value == ConnectionState.Connected) return true
        error.value = "连接已断开，正在尝试重新连接…"
        connectionRepository.nudge()
        return false
    }

    public fun onDraftChange(value: String) = draft.update { value }

    public fun send() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        if (!requireConnectedOrWarn()) return
        val compose = state.value.compose
        val target = activeSessionId.value
        // Desktop `formatAttachedImagesForMessage`: reference images lead, prompt follows.
        val refs = imageEditRefs.value
        val message = if (refs.isEmpty()) text else refs.joinToString("\n") { it.toMarkdown() } + "\n" + text
        // Mid-turn: stage into the shared queue (synced with desktop), don't soft-inject.
        if (state.value.busy && target != null) {
            viewModelScope.launch {
                error.value = null
                when (val result = sessionRepository.pushStaged(target, message)) {
                    is AnyaResult.Success -> {
                        draft.value = ""
                        imageEditRefs.value = emptyList()
                    }
                    is AnyaResult.Failure -> error.value = result.error.userMessage
                }
            }
            return
        }
        viewModelScope.launch {
            sending.value = true
            error.value = null
            val imageGen = if (compose.chatMode == ChatMode.Image) {
                state.value.imageGenCompose.toWire(state.value.imageGenOptions.styles)
            } else {
                null
            }
            when (
                val result = sendChatMessage(
                    target,
                    message,
                    compose.chatMode,
                    compose.toolApprovalMode,
                    compose.chatModel.ifBlank { null },
                    compose.chatModelProvider.ifBlank { null },
                    workspaceId = routeWorkspaceId,
                    imageGen = imageGen,
                )
            ) {
                is AnyaResult.Success -> {
                    draft.value = ""
                    imageEditRefs.value = emptyList()
                    if (target == null) {
                        adoptNewSession(result.data)
                    }
                }
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
            sending.value = false
        }
    }

    public fun guideStaged(index: Int) {
        val sid = activeSessionId.value ?: return
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            when (val result = sessionRepository.guideStaged(sid, index)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
        }
    }

    public fun removeStaged(index: Int) {
        val sid = activeSessionId.value ?: return
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            when (val result = sessionRepository.removeStaged(sid, index)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
        }
    }

    /** Working-tree unified diff for Diff Review (desktop ≥ 0.2.23). */
    public suspend fun loadWorkingTreeDiff(): Result<String> {
        val workspaceId = sessionRepository.sessions.value
            .firstOrNull { it.id == activeSessionId.value }
            ?.workspaceId
            ?: routeWorkspaceId
        return when (val result = sessionRepository.workingTreeDiff(workspaceId)) {
            is AnyaResult.Success -> Result.success(result.data)
            is AnyaResult.Failure -> Result.failure(Exception(result.error.userMessage))
        }
    }

    /** Live desktop environment context for `/context` (desktop ≥ 0.2.23). */
    public suspend fun loadEnvironmentContext(): Result<ai.anya.companion.core.model.workspace.EnvironmentContext> {
        return when (val result = sessionRepository.environmentContext()) {
            is AnyaResult.Success -> Result.success(result.data)
            is AnyaResult.Failure -> Result.failure(Exception(result.error.userMessage))
        }
    }

    /** First send of a `chat/new` screen: switch onto the desktop-assigned session. */
    private suspend fun adoptNewSession(newSessionId: String) {
        if (newSessionId.isBlank() || activeSessionId.value != null) return
        activeSessionId.value = newSessionId
        historyLoading.value = true
        try {
            loadHistory(newSessionId)
        } finally {
            historyLoading.value = false
        }
        refreshCompose(newSessionId)
        sessionRepository.refreshStaged(newSessionId)
        sessionRepository.refreshSessions()
        refreshAttachCatalog()
    }

    public fun stop() {
        val messageId = state.value.activeAssistantMessageId ?: return
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            error.value = null
            when (val result = cancelChatMessage(messageId)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
        }
    }

    public fun setChatMode(mode: ChatMode) {
        val sid = activeSessionId.value
        if (sid == null) {
            draftCompose.update { it.copy(chatMode = mode) }
            return
        }
        viewModelScope.launch { setComposeUseCase(sid, chatMode = mode) }
    }

    public fun setApprovalMode(mode: ToolApprovalMode) {
        val sid = activeSessionId.value
        if (sid == null) {
            draftCompose.update { it.copy(toolApprovalMode = mode) }
            return
        }
        viewModelScope.launch { setComposeUseCase(sid, toolApprovalMode = mode) }
    }

    public fun setModel(model: ChatModelInfo) {
        val sid = activeSessionId.value
        if (sid == null) {
            draftCompose.update {
                it.copy(
                    chatModel = model.id,
                    chatModelProvider = model.provider,
                    chatModelLabel = model.label,
                )
            }
            return
        }
        viewModelScope.launch {
            setComposeUseCase(
                sid,
                chatModel = model.id,
                chatModelProvider = model.provider,
                chatModelLabel = model.label,
            )
        }
    }

    public fun applyThinkingOption(optionId: String) {
        val snapshot = state.value
        val model = findModelEntry(
            snapshot.models,
            snapshot.compose.chatModel,
            snapshot.compose.chatModelProvider,
        )
        when (resolveReasoningControl(model, snapshot.compose.chatModel, snapshot.compose.chatModelProvider)) {
            is ReasoningControl.Effort -> setReasoningEffort(optionId)
            ReasoningControl.Variants -> {
                val variant = model?.thinkingVariants?.find { it.id == optionId } ?: return
                setModel(model.copy(id = variant.id))
            }
            ReasoningControl.None -> Unit
        }
    }

    public fun setReasoningEffort(effort: String) {
        val sid = activeSessionId.value
        if (sid == null) {
            draftCompose.update { it.copy(reasoningEffort = effort) }
        }
        val target = sid ?: NewChatSessionId
        viewModelScope.launch { setComposeUseCase(target, reasoningEffort = effort) }
    }

    public fun refreshContextUsage() {
        if (connectionRepository.connectionState.value != ConnectionState.Connected) return
        viewModelScope.launch {
            val snapshot = state.value
            when (
                val result = refreshContextUsageUseCase(
                    snapshot.sessionId.takeIf { it.isNotBlank() && it != NewChatSessionId },
                    snapshot.draft.takeIf { it.isNotBlank() },
                    snapshot.compose.chatModel.takeIf { it.isNotBlank() },
                )
            ) {
                is AnyaResult.Success -> _contextUsage.value = result.data
                is AnyaResult.Failure -> Unit
            }
        }
    }

    public fun attachInsert(text: String) {
        draft.update { current ->
            if (current.isBlank() || current.endsWith(" ") || current.endsWith("\n")) {
                current + text
            } else {
                "$current $text"
            }
        }
    }

    public fun uploadPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            for (uri in uris) {
                uploadOneUri(uri, asImageRef = false)
            }
        }
    }

    private suspend fun uploadOneUriAsRef(uri: Uri) {
        uploadOneUri(uri, asImageRef = true)
    }

    private suspend fun uploadOneUri(uri: Uri, asImageRef: Boolean) {
        val name = queryDisplayName(uri)
        val mime = appContext.contentResolver.getType(uri)
        _download.value = FileDownloadUiState(
            inProgress = true,
            fileName = name,
            message = "Uploading $name…",
        )
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val knownSize = querySize(uri)
                if (knownSize != null && knownSize > 500L * 1024L * 1024L) {
                    return@withContext AnyaResult.Failure(
                        ai.anya.companion.core.common.result.AnyaError.Protocol(
                            "file too large: $knownSize bytes (max ${500L * 1024L * 1024L})",
                        ),
                    )
                }
                if (knownSize != null && knownSize > 0L) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        uploadLocalFile(
                            sessionId = activeSessionId.value,
                            workspaceId = routeWorkspaceId,
                            fileName = name,
                            size = knownSize,
                            mime = mime,
                            input = input,
                            onProgress = { written, total ->
                                val pct = if (total > 0) (written * 100 / total).toInt() else 0
                                _download.value = FileDownloadUiState(
                                    inProgress = true,
                                    fileName = name,
                                    message = "Uploading $name… $pct%",
                                )
                            },
                        )
                    } ?: AnyaResult.Failure(
                        ai.anya.companion.core.common.result.AnyaError.Unknown("无法读取所选文件"),
                    )
                } else {
                    val tmp = java.io.File.createTempFile("anya-up-", ".bin", appContext.cacheDir)
                    try {
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@withContext AnyaResult.Failure(
                            ai.anya.companion.core.common.result.AnyaError.Unknown("无法读取所选文件"),
                        )
                        tmp.inputStream().use { input ->
                            uploadLocalFile(
                                sessionId = activeSessionId.value,
                                workspaceId = routeWorkspaceId,
                                fileName = name,
                                size = tmp.length(),
                                mime = mime,
                                input = input,
                                onProgress = { written, total ->
                                    val pct = if (total > 0) (written * 100 / total).toInt() else 0
                                    _download.value = FileDownloadUiState(
                                        inProgress = true,
                                        fileName = name,
                                        message = "Uploading $name… $pct%",
                                    )
                                },
                            )
                        }
                    } finally {
                        tmp.delete()
                    }
                }
            }
        }.getOrElse { e ->
            AnyaResult.Failure(
                ai.anya.companion.core.common.result.AnyaError.Unknown(e.message ?: "upload failed", e),
            )
        }
        when (result) {
            is AnyaResult.Success -> {
                val file = result.data
                if (activeSessionId.value == null && file.sessionId.isNotBlank()) {
                    adoptNewSession(file.sessionId)
                }
                val abs = file.absolutePath?.takeIf { it.isNotBlank() }
                val isImage = mime?.startsWith("image/") == true
                // An image picked from the phone always travels as a reference, never as
                // an `@path` mention. The reference is rendered as markdown ahead of the
                // prompt (see the `formatAttachedImagesForMessage` note in the send path)
                // and that is the only form that puts the picture itself into the model's
                // context. A normal pick used to insert `@<desktop path>`, which reached
                // the desktop but left the model holding a path string, not an image.
                val treatAsRef = asImageRef || (isImage && abs != null)
                if (treatAsRef && abs != null) {
                    imageEditRefs.update { current ->
                        current + ImageEditRef(
                            desktopPath = abs,
                            localPreviewUri = uri.toString(),
                        )
                    }
                    seedImagePreviewFromUri(abs, uri, file.name, mime)
                    _download.value = FileDownloadUiState(
                        fileName = file.name,
                        message = "已添加参考图 ${file.name}",
                    )
                } else {
                    attachInsert("@${file.path} ")
                    _localUploads.update { current ->
                        current + LocalUploadItem(name = file.name, path = file.path, size = file.size)
                    }
                    _download.value = FileDownloadUiState(
                        fileName = file.name,
                        message = if ((asImageRef || isImage) && abs == null) {
                            "已上传 ${file.name}（桌面端需 ≥ 0.2.18 才能用作参考图）"
                        } else {
                            "Uploaded ${file.name}"
                        },
                    )
                }
            }
            is AnyaResult.Failure -> {
                _download.value = FileDownloadUiState(
                    fileName = name,
                    message = "Upload failed: ${result.error}",
                )
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else fallback
            } ?: fallback
        }.getOrDefault(fallback).ifBlank { "file" }
    }

    private fun querySize(uri: Uri): Long? {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx < 0 || cursor.isNull(idx)) null else cursor.getLong(idx).takeIf { it > 0L }
            }
        }.getOrNull()
    }

    public fun refreshAttachCatalog() {
        viewModelScope.launch {
            val previous = _attachCatalog.value
            _attachCatalog.update { it.copy(loading = true) }
            val sessionId = activeSessionId.value
            val workspaceId = if (sessionId == null) routeWorkspaceId else null
            val catalog = refreshAttachCatalog(sessionId, workspaceId)
            val mergedSkills = if (catalog.skills.isNotEmpty()) catalog.skills else previous.skills
            val mergedMcp = if (catalog.mcpServers.isNotEmpty()) catalog.mcpServers else previous.mcpServers
            val remoteFiles = catalog.files
            val keepPreviousFiles = workspaceId == null
            val mergedFiles = remoteFiles ?: previous.files.takeIf { keepPreviousFiles }
            val mergedTree = if (remoteFiles != null) {
                buildFileTree(remoteFiles.files)
            } else if (keepPreviousFiles) {
                previous.fileTree
            } else {
                emptyList()
            }
            _attachCatalog.value = AttachCatalogUiState(
                loading = false,
                files = mergedFiles,
                fileTree = mergedTree,
                skills = mergedSkills,
                mcpServers = mergedMcp,
                filesError = catalog.filesError,
                skillsError = catalog.skillsError,
                mcpError = catalog.mcpError,
            )
            if (mergedSkills.isNotEmpty() || mergedMcp.isNotEmpty()) {
                // Persist + remap remote icons to local file:// paths, then refresh UI.
                val cached = loadCachedAttachCatalog.save(mergedSkills, mergedMcp)
                _attachCatalog.update {
                    it.copy(
                        skills = cached.skills.ifEmpty { it.skills },
                        mcpServers = cached.mcpServers.ifEmpty { it.mcpServers },
                    )
                }
            }
        }
    }

    public fun downloadFile(path: String, workspaceId: String? = null) {
        // Kept for attach-tree manual pulls: cache into chat when possible.
        val sid = activeSessionId.value ?: return
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            ingestSharedOffer(
                CompanionFileOffer(
                    sessionId = sid,
                    offerId = UUID.randomUUID().toString(),
                    path = path,
                    name = path.replace('\\', '/').substringAfterLast('/'),
                    workspaceId = workspaceId,
                ),
                autoFetch = true,
            )
        }
    }

    /**
     * Auto-fetch a desktop `path:` image for user-message thumbs when nothing is cached yet.
     * Writes into [imagePreviewCache] (no shared-file inbox card).
     */
    public fun ensureImagePreview(path: String) {
        val trimmed = path.trim().removePrefix("path:")
        if (trimmed.isBlank()) return
        val key = trimmed.replace('\\', '/')
        if (autoFetchedImagePaths.contains(key)) return
        if (findCachedImagePreview(trimmed) != null) return
        val sid = activeSessionId.value ?: return
        if (connectionRepository.connectionState.value != ConnectionState.Connected) return
        autoFetchedImagePaths += key
        viewModelScope.launch {
            when (
                val result = downloadWorkspaceFile(
                    path = trimmed,
                    sessionId = sid,
                    workspaceId = routeWorkspaceId,
                )
            ) {
                is AnyaResult.Success -> {
                    val file = result.data
                    imagePreviewCache.update {
                        it + (
                            key to ChatSharedFile(
                                offerId = "preview-$key",
                                path = trimmed,
                                name = file.name,
                                mime = file.mime,
                                size = file.size,
                                localPath = file.localPath,
                                status = SharedFileStatus.Ready,
                            )
                            )
                    }
                }
                is AnyaResult.Failure -> {
                    autoFetchedImagePaths.remove(key)
                }
            }
        }
    }

    private fun findCachedImagePreview(path: String): ChatSharedFile? {
        val ready = imagePreviewCache.value.values.firstOrNull {
            it.status == SharedFileStatus.Ready && sameDesktopPath(it.path, path)
        }
        if (ready != null) return ready
        return findLocalSharedImage(
            state.value.messages.asSequence().flatMap { it.sharedFiles.asSequence() },
            path,
        )?.takeIf { it.status == SharedFileStatus.Ready && !it.localPath.isNullOrBlank() }
    }

    /** Copy an album content URI into the shared cache so history can show the ref immediately. */
    private fun seedImagePreviewFromUri(
        desktopPath: String,
        uri: Uri,
        name: String,
        mime: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(appContext.filesDir, "shared").apply { mkdirs() }
                val safeName = name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "ref.jpg" }
                var target = java.io.File(dir, "ref-$safeName")
                if (target.exists()) {
                    val stem = safeName.substringBeforeLast('.', safeName)
                    val ext = safeName.substringAfterLast('.', "")
                    var i = 1
                    while (target.exists()) {
                        target = java.io.File(
                            dir,
                            if (ext.isEmpty()) "ref-$stem-$i" else "ref-$stem-$i.$ext",
                        )
                        i++
                    }
                }
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching
                val key = desktopPath.replace('\\', '/')
                val cached = ChatSharedFile(
                    offerId = "preview-$key",
                    path = desktopPath,
                    name = name,
                    mime = mime?.takeIf { it.isNotBlank() } ?: guessMime(name),
                    size = target.length(),
                    localPath = target.absolutePath,
                    status = SharedFileStatus.Ready,
                )
                imagePreviewCache.update { it + (key to cached) }
                autoFetchedImagePaths += key
            }
        }
    }

    private fun sameDesktopPath(a: String, b: String): Boolean =
        a.replace('\\', '/').trimEnd('/').equals(b.replace('\\', '/').trimEnd('/'), ignoreCase = true)

    public fun exportSharedFile(offerId: String) {
        val sid = activeSessionId.value ?: return
        val messageFile = state.value.messages
            .asSequence()
            .flatMap { it.sharedFiles.asSequence() }
            .firstOrNull { it.offerId == offerId && it.status == SharedFileStatus.Ready }
        // Generated images and reply `![…](path:…)` thumbnails live only in imagePreviewCache
        // (offerId "preview-…"), never in message.sharedFiles — resolve them too or "下载" no-ops.
        val cachedFile = if (messageFile != null) null else {
            imagePreviewCache.value.values.firstOrNull {
                it.offerId == offerId && it.status == SharedFileStatus.Ready
            }
        }
        val file = messageFile ?: cachedFile ?: return
        val localPath = file.localPath ?: return
        if (!file.exportedUri.isNullOrBlank()) return
        _download.value = FileDownloadUiState(
            inProgress = true,
            fileName = file.name,
            message = "Saving ${file.name}…",
        )
        viewModelScope.launch {
            when (val result = exportCachedFile(localPath, file.name, file.mime)) {
                is AnyaResult.Success -> {
                    if (messageFile != null) {
                        sessionRepository.patchLocalSharedFile(sid, offerId) { current ->
                            current.copy(exportedUri = result.data)
                        }
                    } else {
                        imagePreviewCache.update { current ->
                            current.mapValues { (_, v) ->
                                if (v.offerId == offerId) v.copy(exportedUri = result.data) else v
                            }
                        }
                    }
                    _download.value = FileDownloadUiState(
                        fileName = file.name,
                        message = "Saved to Downloads: ${file.name}",
                        localUri = result.data,
                        mime = file.mime,
                    )
                }
                is AnyaResult.Failure -> {
                    _download.value = FileDownloadUiState(
                        fileName = file.name,
                        message = "Save failed: ${result.error}",
                    )
                }
            }
        }
    }

    public fun dismissDownloadNotice() {
        if (!_download.value.inProgress) {
            _download.value = FileDownloadUiState()
        }
    }

    public fun approvePlan(messageId: String) {
        val sid = activeSessionId.value ?: return
        if (messageId in planApprovedMessageIds.value) return
        if (!requireConnectedOrWarn()) return
        planApprovedMessageIds.update { it + messageId }
        viewModelScope.launch {
            when (val result = approvePlanUseCase(sid)) {
                is AnyaResult.Failure -> {
                    planApprovedMessageIds.update { it - messageId }
                    error.value = result.error.userMessage
                }
                is AnyaResult.Success -> {
                    error.value = null
                }
            }
        }
    }

    public fun answerAsk(
        selectedByIndex: Map<Int, List<String>>,
        skipped: Boolean = false,
    ) {
        val ask = state.value.pendingAsk ?: return
        if (!requireConnectedOrWarn()) return
        val questions = ask.questions
        val payload = buildJsonObject {
            put("skipped", skipped)
            put(
                "answers",
                buildJsonArray {
                    questions.forEachIndexed { index, question ->
                        val selected = selectedByIndex[index].orEmpty()
                        val userSupplement = skipped && selected.isEmpty()
                        add(
                            buildJsonObject {
                                put("header", question.header)
                                put("question", question.question)
                                put(
                                    "selected",
                                    buildJsonArray {
                                        if (!userSupplement) {
                                            selected.forEach { add(JsonPrimitive(it)) }
                                        }
                                    },
                                )
                                put("userSupplement", userSupplement)
                            },
                        )
                    }
                },
            )
        }
        viewModelScope.launch {
            when (val result = respondAsk(ask.requestId, payload.toString())) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
        }
    }
    public fun answerToolApproval(decision: ai.anya.companion.core.model.approval.ApprovalDecision) {
        val ask = state.value.pendingAsk ?: return
        if (ask.kind != ApprovalKind.Tool && ask.kind != ApprovalKind.PathPermission) return
        if (!requireConnectedOrWarn()) return
        viewModelScope.launch {
            when (val result = respondApproval(ask.requestId, decision)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.userMessage
            }
        }
    }
}

/** Prefer desktop label, else look up the live models list, else id fallback. */
private fun resolveComposeLabel(
    compose: SessionCompose,
    models: List<ChatModelInfo>,
): SessionCompose {
    if (!compose.chatModelLabel.isNullOrBlank()) return compose
    val match = models.firstOrNull { model ->
        model.id == compose.chatModel &&
            (compose.chatModelProvider.isBlank() || model.provider == compose.chatModelProvider)
    } ?: models.firstOrNull { it.id == compose.chatModel }
    val label = match?.label ?: return compose
    return compose.copy(chatModelLabel = label)
}

internal fun buildFileTree(paths: List<String>): List<FileNode> {
    class MutableNode(
        val name: String,
        val path: String,
        var isDirectory: Boolean,
        val children: MutableMap<String, MutableNode> = linkedMapOf(),
    )

    val rootChildren = linkedMapOf<String, MutableNode>()
    paths.forEach { raw ->
        val normalized = raw.replace('\\', '/').trim('/').ifBlank { return@forEach }
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return@forEach
        var cursor: MutableMap<String, MutableNode> = rootChildren
        var prefix = ""
        parts.forEachIndexed { index, part ->
            prefix = if (prefix.isEmpty()) part else "$prefix/$part"
            val isDir = index < parts.lastIndex
            val existing = cursor[part]
            if (existing == null) {
                val created = MutableNode(name = part, path = prefix, isDirectory = isDir)
                cursor[part] = created
                cursor = created.children
            } else {
                if (isDir) existing.isDirectory = true
                cursor = existing.children
            }
        }
    }

    fun toFileNode(node: MutableNode): FileNode = FileNode(
        path = node.path,
        name = node.name,
        isDirectory = node.isDirectory || node.children.isNotEmpty(),
        children = node.children.values
            .sortedWith(
                compareByDescending<MutableNode> { it.isDirectory || it.children.isNotEmpty() }
                    .thenBy { it.name.lowercase() },
            )
            .map(::toFileNode),
    )

    return rootChildren.values
        .sortedWith(
            compareByDescending<MutableNode> { it.isDirectory || it.children.isNotEmpty() }
                .thenBy { it.name.lowercase() },
        )
        .map(::toFileNode)
}
