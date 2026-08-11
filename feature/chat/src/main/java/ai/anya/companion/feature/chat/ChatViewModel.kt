package ai.anya.companion.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.usecase.ApprovePlanUseCase
import ai.anya.companion.core.domain.usecase.CancelChatMessageUseCase
import ai.anya.companion.core.domain.usecase.LoadHistoryUseCase
import ai.anya.companion.core.domain.usecase.ObserveApprovalsUseCase
import ai.anya.companion.core.domain.usecase.ObserveComposeUseCase
import ai.anya.companion.core.domain.usecase.ObserveMessagesUseCase
import ai.anya.companion.core.domain.usecase.ObserveModelsUseCase
import ai.anya.companion.core.domain.usecase.ObservePlanTasksUseCase
import ai.anya.companion.core.domain.usecase.RefreshAttachCatalogUseCase
import ai.anya.companion.core.domain.usecase.RefreshComposeUseCase
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
import ai.anya.companion.core.model.session.MessageStatus
import ai.anya.companion.core.model.session.PlanTaskItem
import ai.anya.companion.core.model.session.SessionCompose
import ai.anya.companion.core.model.session.ToolApprovalMode
import ai.anya.companion.core.model.workspace.FileNode
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import ai.anya.companion.core.model.workspace.WorkspaceFilesCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    /** When opened from search, scroll to this message once history is ready. */
    public val focusMessageId: String? = null,
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

/** Intermediate tuple to work around [combine]'s five-flow arity limit. */
private data class ChatCoreState(
    val messages: List<ChatMessage>,
    val sessions: List<ai.anya.companion.core.model.session.ChatSessionSummary>,
    val draft: String,
    val sending: Boolean,
    val error: String?,
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
    private val loadHistory: LoadHistoryUseCase,
    private val sendChatMessage: SendChatMessageUseCase,
    private val cancelChatMessage: CancelChatMessageUseCase,
    private val refreshCompose: RefreshComposeUseCase,
    private val setComposeUseCase: SetComposeUseCase,
    private val refreshModels: RefreshModelsUseCase,
    private val approvePlanUseCase: ApprovePlanUseCase,
    private val refreshAttachCatalog: RefreshAttachCatalogUseCase,
    private val respondAsk: RespondAskUseCase,
    private val respondApproval: ai.anya.companion.core.domain.usecase.RespondApprovalUseCase,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val focusMessageId: String? = savedStateHandle["messageId"]

    private val draft = MutableStateFlow("")
    private val sending = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val _attachCatalog = MutableStateFlow(AttachCatalogUiState())
    public val attachCatalog: StateFlow<AttachCatalogUiState> = _attachCatalog.asStateFlow()

    private val pendingAskForSession = observeApprovals().map { list ->
        list.firstOrNull {
            it.sessionId == sessionId &&
                (it.kind == ApprovalKind.AskUser || it.kind == ApprovalKind.Tool)
        }
    }

    private val core: StateFlow<ChatCoreState> = combine(
        observeMessages(sessionId),
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

    public val state: StateFlow<ChatUiState> = combine(
        core,
        observeCompose(sessionId),
        observeModels(),
        observePlanTasks(sessionId),
        pendingAskForSession,
    ) { c, compose, models, tasks, pendingAsk ->
        val session = c.sessions.firstOrNull { it.id == sessionId }
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
            sessionId = sessionId,
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
            focusMessageId = focusMessageId,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatUiState(sessionId = sessionId, focusMessageId = focusMessageId),
    )

    init {
        viewModelScope.launch {
            connectionRepository.connectionState
                .map { it == ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected ->
                    if (!connected) return@collect
                    loadHistory(sessionId)
                    refreshCompose(sessionId)
                    refreshModels()
                }
        }
    }

    public fun onDraftChange(value: String) = draft.update { value }

    public fun send() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        val compose = state.value.compose
        viewModelScope.launch {
            sending.value = true
            error.value = null
            when (
                val result = sendChatMessage(
                    sessionId,
                    text,
                    compose.chatMode,
                    compose.toolApprovalMode,
                    compose.chatModel.ifBlank { null },
                    compose.chatModelProvider.ifBlank { null },
                )
            ) {
                is AnyaResult.Success -> draft.value = ""
                is AnyaResult.Failure -> error.value = result.error.toString()
            }
            sending.value = false
        }
    }

    public fun stop() {
        val messageId = state.value.activeAssistantMessageId ?: return
        viewModelScope.launch {
            error.value = null
            when (val result = cancelChatMessage(messageId)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.toString()
            }
        }
    }

    public fun setChatMode(mode: ChatMode) {
        viewModelScope.launch { setComposeUseCase(sessionId, chatMode = mode) }
    }

    public fun setApprovalMode(mode: ToolApprovalMode) {
        viewModelScope.launch { setComposeUseCase(sessionId, toolApprovalMode = mode) }
    }

    public fun setModel(model: ChatModelInfo) {
        viewModelScope.launch {
            setComposeUseCase(
                sessionId,
                chatModel = model.id,
                chatModelProvider = model.provider,
                chatModelLabel = model.label,
            )
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

    public fun refreshAttachCatalog() {
        viewModelScope.launch {
            _attachCatalog.update { it.copy(loading = true) }
            val catalog = refreshAttachCatalog(sessionId)
            _attachCatalog.value = AttachCatalogUiState(
                loading = false,
                files = catalog.files,
                fileTree = buildFileTree(catalog.files?.files.orEmpty()),
                skills = catalog.skills,
                mcpServers = catalog.mcpServers,
                filesError = catalog.filesError,
                skillsError = catalog.skillsError,
                mcpError = catalog.mcpError,
            )
        }
    }

    public fun approvePlan() {
        viewModelScope.launch {
            when (val result = approvePlanUseCase(sessionId)) {
                is AnyaResult.Failure -> {
                    error.value = result.error.toString()
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
                is AnyaResult.Failure -> error.value = result.error.toString()
            }
        }
    }
    public fun answerToolApproval(decision: ai.anya.companion.core.model.approval.ApprovalDecision) {
        val ask = state.value.pendingAsk ?: return
        if (ask.kind != ApprovalKind.Tool) return
        viewModelScope.launch {
            when (val result = respondApproval(ask.requestId, decision)) {
                is AnyaResult.Success -> Unit
                is AnyaResult.Failure -> error.value = result.error.toString()
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
