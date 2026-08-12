package ai.anya.companion.core.data.repository

import ai.anya.companion.core.common.di.ApplicationScope
import ai.anya.companion.core.common.result.AnyaError
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.ApprovalRepository
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.repository.WorkspaceRepository
import ai.anya.companion.core.data.local.AttachCatalogStore
import ai.anya.companion.core.data.local.CredentialStore
import ai.anya.companion.core.model.approval.ApprovalDecision
import ai.anya.companion.core.model.approval.ApprovalKind
import ai.anya.companion.core.model.approval.AskUserQuestion
import ai.anya.companion.core.model.approval.PendingApproval
import ai.anya.companion.core.model.protocol.ApprovalDecisionWire
import ai.anya.companion.core.model.protocol.ClientMessage
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.core.model.protocol.PairingPayload
import ai.anya.companion.core.model.protocol.ServerMessage
import ai.anya.companion.core.model.session.AgentRunState
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatMode
import ai.anya.companion.core.model.session.ChatModelInfo
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.ChatSessionSummary
import ai.anya.companion.core.model.session.CodeChangeEntry
import ai.anya.companion.core.model.session.MessageStatus
import ai.anya.companion.core.model.session.PlanTaskItem
import ai.anya.companion.core.model.session.SessionCompose
import ai.anya.companion.core.model.session.SessionSearchHit
import ai.anya.companion.core.model.session.SessionSearchMatchKind
import ai.anya.companion.core.model.session.ToolActivity
import ai.anya.companion.core.model.session.ToolApprovalMode
import ai.anya.companion.core.model.session.ToolPreviewPayload
import ai.anya.companion.core.model.session.wireValue
import ai.anya.companion.core.model.workspace.FileContent
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import ai.anya.companion.core.model.workspace.WorkspaceFilesCatalog
import ai.anya.companion.core.model.workspace.WorkspaceSnapshot
import ai.anya.companion.core.model.workspace.WorkspaceSummary
import ai.anya.companion.core.network.gateway.GatewaySocketState
import ai.anya.companion.core.network.gateway.RemoteGatewayClient
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class DefaultConnectionRepository @Inject constructor(
    private val credentialStore: CredentialStore,
    private val gateway: RemoteGatewayClient,
    @ApplicationScope private val appScope: CoroutineScope,
) : ConnectionRepository {

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _credential = MutableStateFlow<DeviceCredential?>(null)
    override val credential: StateFlow<DeviceCredential?> = _credential.asStateFlow()

    /** When true, keep trying to stay online whenever a credential exists. */
    private val wantConnected = MutableStateFlow(true)
    private val connectMutex = Mutex()
    private var reconnectJob: Job? = null

    init {
        appScope.launch {
            credentialStore.credentialFlow.collect { saved ->
                _credential.value = saved
                if (saved == null) {
                    wantConnected.value = true
                    reconnectJob?.cancel()
                    gateway.disconnect()
                    _connectionState.value = ConnectionState.Disconnected
                } else if (wantConnected.value) {
                    ensureConnected(saved)
                }
            }
        }
        appScope.launch {
            gateway.state.collect { socketState ->
                when (socketState) {
                    GatewaySocketState.Connecting -> {
                        if (_connectionState.value != ConnectionState.Reconnecting) {
                            _connectionState.value = ConnectionState.Connecting
                        }
                    }
                    GatewaySocketState.Open -> {
                        // Authenticated Connected is set only after hello.ok.
                        if (_connectionState.value != ConnectionState.Connected &&
                            _connectionState.value != ConnectionState.Reconnecting
                        ) {
                            _connectionState.value = ConnectionState.Connecting
                        }
                    }
                    GatewaySocketState.Failed -> {
                        _connectionState.value = ConnectionState.Error
                        scheduleReconnect()
                    }
                    GatewaySocketState.Idle,
                    GatewaySocketState.Closed,
                    GatewaySocketState.Closing,
                    -> {
                        if (wantConnected.value && _credential.value != null) {
                            if (_connectionState.value == ConnectionState.Connected) {
                                _connectionState.value = ConnectionState.Reconnecting
                            }
                            scheduleReconnect()
                        } else if (!wantConnected.value) {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                    }
                }
            }
        }
        appScope.launch {
            gateway.incoming.collect { message ->
                when (message) {
                    is ServerMessage.HelloOk -> {
                        reconnectJob?.cancel()
                        _connectionState.value = ConnectionState.Connected
                    }
                    is ServerMessage.HelloError -> {
                        _connectionState.value = ConnectionState.Error
                        wantConnected.value = false
                        gateway.disconnect()
                    }
                    is ServerMessage.Ping -> {
                        // Bidirectional keep-alive for Cloudflare / NAT (WS pings alone often die).
                        gateway.send(ClientMessage.Pong(message.ts))
                    }
                    else -> Unit
                }
            }
        }
        // Safety net: if we ever sit in Connecting/Reconnecting for too long — e.g. a
        // socket opens but, for whatever reason, nothing ends up sending hello for it —
        // don't leave the user staring at "连接中" forever (or until they notice and
        // manually disconnect/reconnect). Force a clean restart of the connect flow.
        appScope.launch {
            var stuckWatchdog: Job? = null
            connectionState.collect { current ->
                stuckWatchdog?.cancel()
                if (current == ConnectionState.Connecting || current == ConnectionState.Reconnecting) {
                    stuckWatchdog = launch {
                        delay(15_000)
                        if (_connectionState.value != current) return@launch
                        if (!wantConnected.value) return@launch
                        Timber.w("Connection stuck in %s for 15s; forcing a clean reconnect", current)
                        gateway.disconnect()
                        reconnectJob?.cancel()
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    override suspend fun pair(payload: PairingPayload): AnyaResult<DeviceCredential> {
        // Desktop pairing handshake will replace this stub once Gateway lands on Anya PC.
        val credential = DeviceCredential(
            deviceId = UUID.randomUUID().toString(),
            credential = payload.pairingToken,
            host = payload.host,
            port = payload.port,
            scheme = payload.scheme,
            pairedAtEpochMs = System.currentTimeMillis(),
        )
        credentialStore.save(credential)
        _credential.value = credential
        wantConnected.value = true
        reconnectJob?.cancel()
        ensureConnected(credential)
        return AnyaResult.Success(credential)
    }

    override suspend fun connect(): AnyaResult<Unit> {
        val credential = _credential.value
            ?: return AnyaResult.Failure(AnyaError.NotPaired())
        wantConnected.value = true
        reconnectJob?.cancel()
        return ensureConnected(credential)
    }

    override suspend fun disconnect() {
        wantConnected.value = false
        reconnectJob?.cancel()
        gateway.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun clearPairing() {
        disconnect()
        credentialStore.clear()
        _credential.value = null
    }

    private fun scheduleReconnect() {
        if (!wantConnected.value || _credential.value == null) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = appScope.launch {
            var attempt = 0
            while (isActive && wantConnected.value) {
                val credential = _credential.value ?: return@launch
                val state = gateway.state.value
                if (state == GatewaySocketState.Open) return@launch
                if (state == GatewaySocketState.Connecting) {
                    delay(500)
                    continue
                }
                _connectionState.value = ConnectionState.Reconnecting
                attempt += 1
                // Faster first retries help Cloudflare Tunnel blips recover quickly.
                val backoffMs = when (attempt) {
                    1 -> 300L
                    2 -> 700L
                    3 -> 1_500L
                    4 -> 3_000L
                    else -> 8_000L
                }
                delay(backoffMs)
                if (!wantConnected.value) return@launch
                ensureConnected(credential)
                if (gateway.state.value == GatewaySocketState.Open) return@launch
            }
        }
    }

    // NOTE: deliberately does NOT cancel `reconnectJob` here — `scheduleReconnect()`'s
    // own retry loop calls this function on every attempt, and it *is* running inside
    // that same job. Cancelling it from in here would cancel its own coroutine mid-call,
    // silently killing the retry loop after a single attempt (it would throw out of this
    // very suspend call via CancellationException). Callers that need to supersede a
    // pending reconnect loop (e.g. an explicit user-initiated connect) cancel it themselves
    // before calling in.
    private suspend fun ensureConnected(credential: DeviceCredential): AnyaResult<Unit> =
        connectMutex.withLock {
            when (gateway.state.value) {
                GatewaySocketState.Open -> {
                    if (_connectionState.value == ConnectionState.Connected) {
                        AnyaResult.Success(Unit)
                    } else {
                        // Socket is open but auth may not have finished — re-hello.
                        sendHello(credential)
                    }
                }
                GatewaySocketState.Connecting -> {
                    val ready = waitForOpenOrFail()
                    if (ready is AnyaResult.Failure) return@withLock ready
                    sendHello(credential)
                }
                else -> {
                    if (_connectionState.value != ConnectionState.Reconnecting) {
                        _connectionState.value = ConnectionState.Connecting
                    }
                    val opened = gateway.connect(credential)
                    if (opened is AnyaResult.Failure) {
                        _connectionState.value = ConnectionState.Error
                        return@withLock opened
                    }
                    val ready = waitForOpenOrFail()
                    if (ready is AnyaResult.Failure) return@withLock ready
                    sendHello(credential)
                }
            }
        }

    private suspend fun sendHello(credential: DeviceCredential): AnyaResult<Unit> {
        if (_connectionState.value == ConnectionState.Connected) {
            // Force callers to wait for a fresh auth edge when re-helloing.
            _connectionState.value = ConnectionState.Connecting
        }
        val sent = gateway.send(
            ClientMessage.Hello(
                deviceId = credential.deviceId,
                credential = credential.credential,
                appVersion = "0.1.0",
            ),
        )
        if (sent is AnyaResult.Failure) {
            _connectionState.value = ConnectionState.Error
            return sent
        }
        // hello.ok flips Connected via the incoming collector; wait briefly so callers
        // that refresh right after connect() see an authenticated session.
        val authed = withTimeoutOrNull(10_000) {
            connectionState.first { it == ConnectionState.Connected || it == ConnectionState.Error }
        }
        return when (authed) {
            ConnectionState.Connected -> AnyaResult.Success(Unit)
            ConnectionState.Error -> AnyaResult.Failure(AnyaError.Unauthorized("hello rejected"))
            else -> AnyaResult.Failure(AnyaError.Network("hello timed out"))
        }
    }

    private suspend fun waitForOpenOrFail(): AnyaResult<Unit> {
        val terminal = withTimeoutOrNull(RemoteGatewayClient.CONNECT_TIMEOUT_MS) {
            gateway.state.first {
                it == GatewaySocketState.Open ||
                    it == GatewaySocketState.Failed ||
                    it == GatewaySocketState.Closed
            }
        }
        return when (terminal) {
            GatewaySocketState.Open -> AnyaResult.Success(Unit)
            null -> {
                gateway.disconnect()
                _connectionState.value = ConnectionState.Error
                AnyaResult.Failure(AnyaError.Network("连接超时（超过 1 分钟），已自动断开"))
            }
            else -> AnyaResult.Failure(AnyaError.Network("Gateway connect failed"))
        }
    }
}

@Singleton
public class DefaultSessionRepository @Inject constructor(
    private val gateway: RemoteGatewayClient,
    private val json: Json,
    @ApplicationScope private val appScope: CoroutineScope,
) : SessionRepository {

    private val _sessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    override val sessions: StateFlow<List<ChatSessionSummary>> = _sessions.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceSummary>>(emptyList())
    override val workspaces: StateFlow<List<WorkspaceSummary>> = _workspaces.asStateFlow()

    private val messagesBySession = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val composeBySession = MutableStateFlow<Map<String, SessionCompose>>(emptyMap())
    private val tasksBySession = MutableStateFlow<Map<String, List<PlanTaskItem>>>(emptyMap())
    private val _models = MutableStateFlow<List<ChatModelInfo>>(emptyList())

    init {
        appScope.launch {
            gateway.incoming.collect { message ->
                when (message) {
                    is ServerMessage.HelloOk -> {
                        // Desktop also pushes session.snapshot after hello; pull explicitly
                        // so a missed event still fills the home list after cold start.
                        appScope.launch {
                            refreshSessions()
                            refreshModels()
                        }
                    }
                    is ServerMessage.Event -> handleEvent(message.name, message.data)
                    is ServerMessage.RpcResult -> handleRpc(message)
                    else -> Unit
                }
            }
        }
    }

    override fun messages(sessionId: String): Flow<List<ChatMessage>> =
        messagesBySession.map { it[sessionId].orEmpty() }

    override fun compose(sessionId: String): Flow<SessionCompose> =
        composeBySession.map { it[sessionId] ?: SessionCompose() }

    override fun models(): StateFlow<List<ChatModelInfo>> = _models.asStateFlow()

    override fun planTasks(sessionId: String): Flow<List<PlanTaskItem>> =
        tasksBySession.map { it[sessionId].orEmpty() }

    override suspend fun refreshSessions(): AnyaResult<List<ChatSessionSummary>> {
        val requestId = UUID.randomUUID().toString()
        return when (val result = gateway.request(ClientMessage.SessionList(requestId), requestId)) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "session.list failed"))
                } else {
                    // handleRpc() (fed by gateway.incoming) applies the same payload to
                    // _sessions as a side effect of this same response; by the time we
                    // get here that update has already happened, but read defensively.
                    AnyaResult.Success(_sessions.value)
                }
            }
        }
    }

    override suspend fun loadHistory(sessionId: String): AnyaResult<List<ChatMessage>> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(ClientMessage.SessionHistory(requestId, sessionId), requestId)
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "session.history failed"))
                } else {
                    val payload = rpc.data as? JsonObject
                    val messages = payload?.get("messages")?.let { element ->
                        runCatching {
                            json.decodeFromJsonElement<List<ChatMessage>>(element)
                        }.getOrNull()
                    }
                    if (messages != null) {
                        messagesBySession.update { it + (sessionId to messages) }
                        AnyaResult.Success(messages)
                    } else {
                        AnyaResult.Success(messagesBySession.value[sessionId].orEmpty())
                    }
                }
            }
        }
    }

    override suspend fun findSessionsByMessage(
        query: String,
        excludeSessionIds: Set<String>,
    ): List<SessionSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val targets = _sessions.value.filter { it.id !in excludeSessionIds }
        if (targets.isEmpty()) return emptyList()
        val gate = Semaphore(permits = 4)
        return coroutineScope {
            targets.map { session ->
                async {
                    gate.withPermit {
                        val cached = messagesBySession.value[session.id]
                        val messages = cached ?: when (val loaded = loadHistory(session.id)) {
                            is AnyaResult.Success -> loaded.data
                            is AnyaResult.Failure -> emptyList()
                        }
                        val match = messages.firstOrNull { message ->
                            message.content.contains(needle, ignoreCase = true) ||
                                message.reasoning.orEmpty().contains(needle, ignoreCase = true)
                        } ?: return@withPermit null
                        val source = match.content.ifBlank { match.reasoning.orEmpty() }
                        SessionSearchHit(
                            session = session,
                            matchKind = SessionSearchMatchKind.Message,
                            snippet = snippetAround(source, needle),
                            messageId = match.id,
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun sendMessage(
        sessionId: String?,
        message: String,
        chatMode: ChatMode?,
        toolApprovalMode: ToolApprovalMode?,
        chatModel: String?,
        chatModelProvider: String?,
    ): AnyaResult<String> {
        val requestId = UUID.randomUUID().toString()
        val targetSession = sessionId.orEmpty()
        if (targetSession.isNotBlank()) {
            val optimistic = ChatMessage(
                id = "local-$requestId",
                sessionId = targetSession,
                role = ChatRole.User,
                content = message,
                status = MessageStatus.Complete,
                createdAtEpochMs = System.currentTimeMillis(),
            )
            upsertMessage(targetSession, optimistic)
        }
        val send = gateway.send(
            ClientMessage.ChatSend(
                requestId = requestId,
                sessionId = sessionId,
                message = message,
                chatMode = chatMode?.wireValue(),
                toolApprovalMode = toolApprovalMode?.wireValue(),
                chatModel = chatModel,
                chatModelProvider = chatModelProvider,
            ),
        )
        return when (send) {
            is AnyaResult.Success -> AnyaResult.Success(requestId)
            is AnyaResult.Failure -> send
        }
    }

    override suspend fun cancel(messageId: String): AnyaResult<Unit> {
        val result = gateway.send(
            ClientMessage.ChatCancel(
                requestId = UUID.randomUUID().toString(),
                messageId = messageId,
            ),
        )
        if (result is AnyaResult.Success) {
            messagesBySession.update { map ->
                map.mapValues { (_, messages) ->
                    messages.map { message ->
                        if (message.id == messageId &&
                            (message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending)
                        ) {
                            message.copy(status = MessageStatus.Cancelled)
                        } else {
                            message
                        }
                    }
                }
            }
            val sessionId = messagesBySession.value.entries
                .firstOrNull { (_, messages) -> messages.any { it.id == messageId } }
                ?.key
            if (sessionId != null) {
                patchRunState(sessionId, AgentRunState.Idle)
            }
        }
        return result
    }

    override suspend fun refreshCompose(sessionId: String): AnyaResult<SessionCompose> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(
                ClientMessage.SessionComposeGet(requestId, sessionId),
                requestId,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "session.compose.get failed"))
                } else {
                    val payload = rpc.data as? JsonObject
                    val compose = payload?.get("compose")?.let { element ->
                        runCatching { json.decodeFromJsonElement<SessionCompose>(element) }.getOrNull()
                    }
                    if (compose != null) {
                        composeBySession.update { it + (sessionId to compose) }
                        AnyaResult.Success(compose)
                    } else {
                        AnyaResult.Success(composeBySession.value[sessionId] ?: SessionCompose())
                    }
                }
            }
        }
    }

    override suspend fun setCompose(
        sessionId: String,
        chatMode: ChatMode?,
        toolApprovalMode: ToolApprovalMode?,
        chatModel: String?,
        chatModelProvider: String?,
        chatModelLabel: String?,
    ): AnyaResult<SessionCompose> {
        val requestId = UUID.randomUUID().toString()
        val current = composeBySession.value[sessionId] ?: SessionCompose()
        val optimistic = current.copy(
            chatMode = chatMode ?: current.chatMode,
            toolApprovalMode = toolApprovalMode ?: current.toolApprovalMode,
            chatModel = chatModel ?: current.chatModel,
            chatModelProvider = chatModelProvider ?: current.chatModelProvider,
            chatModelLabel = chatModelLabel ?: current.chatModelLabel,
        )
        composeBySession.update { it + (sessionId to optimistic) }
        return when (
            val result = gateway.request(
                ClientMessage.SessionComposeSet(
                    requestId = requestId,
                    sessionId = sessionId,
                    chatMode = chatMode?.wireValue(),
                    toolApprovalMode = toolApprovalMode?.wireValue(),
                    chatModel = chatModel,
                    chatModelProvider = chatModelProvider,
                    chatModelLabel = chatModelLabel,
                ),
                requestId,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "session.compose.set failed"))
                } else {
                    val payload = rpc.data as? JsonObject
                    val compose = payload?.get("compose")?.let { element ->
                        runCatching { json.decodeFromJsonElement<SessionCompose>(element) }.getOrNull()
                    } ?: optimistic
                    composeBySession.update { it + (sessionId to compose) }
                    AnyaResult.Success(compose)
                }
            }
        }
    }

    override suspend fun refreshModels(): AnyaResult<List<ChatModelInfo>> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(
                ClientMessage.ModelsList(requestId),
                requestId,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "models.list failed"))
                } else {
                    val payload = rpc.data as? JsonObject
                    val models = payload?.get("models")?.let { element ->
                        runCatching {
                            json.decodeFromJsonElement<List<ChatModelInfo>>(element)
                        }.getOrElse { emptyList() }
                    }.orEmpty()
                    _models.value = models
                    AnyaResult.Success(models)
                }
            }
        }
    }

    override suspend fun approvePlan(sessionId: String): AnyaResult<Unit> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(
                ClientMessage.PlanApprove(requestId, sessionId),
                requestId,
                timeoutMs = 30_000,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "plan.approve failed"))
                } else {
                    // Optimistic bubble — Desktop persists the same text via resumePlan send.
                    val approveText =
                        "计划已批准。现在按批准的计划执行，本回合写操作已解除限制。"
                    upsertMessage(
                        sessionId,
                        ChatMessage(
                            id = "local-user-plan-$requestId",
                            sessionId = sessionId,
                            role = ChatRole.User,
                            content = approveText,
                            status = MessageStatus.Complete,
                            createdAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    touchSession(sessionId)
                    patchRunState(sessionId, AgentRunState.Streaming)
                    AnyaResult.Success(Unit)
                }
            }
        }
    }

    private fun handleEvent(name: String, data: JsonObject) {
        when (name) {
            "session.snapshot" -> applySnapshot(data)
            "session.status" -> {
                val sessionId = data.string("sessionId") ?: return
                val runState = data.string("runState")?.let { runCatching {
                    AgentRunState.valueOf(it)
                }.getOrNull() } ?: return
                _sessions.update { list ->
                    list.map { session ->
                        if (session.id == sessionId) session.copy(runState = runState) else session
                    }
                }
            }
            "session.title" -> {
                val sessionId = data.string("sessionId") ?: return
                val title = data.string("title") ?: return
                _sessions.update { list ->
                    list.map { session ->
                        if (session.id == sessionId) session.copy(title = title) else session
                    }
                }
            }
            "session.compose" -> {
                val sessionId = data.string("sessionId") ?: return
                val composeElement = data["compose"] ?: return
                val compose = runCatching {
                    json.decodeFromJsonElement<SessionCompose>(composeElement)
                }.getOrNull() ?: return
                composeBySession.update { it + (sessionId to compose) }
            }
            "session.tasks" -> {
                val sessionId = data.string("sessionId") ?: return
                val tasksElement = data["tasks"] ?: return
                val tasks = runCatching {
                    json.decodeFromJsonElement<List<PlanTaskItem>>(tasksElement)
                }.getOrElse { emptyList() }
                tasksBySession.update { it + (sessionId to tasks) }
                messagesBySession.update { map ->
                    val current = map[sessionId].orEmpty().toMutableList()
                    val idx = current.indexOfLast {
                        it.role == ChatRole.Assistant &&
                            (it.status == MessageStatus.Streaming || it.status == MessageStatus.Pending)
                    }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(planTasks = tasks)
                    }
                    map + (sessionId to current)
                }
            }
            "chat.started" -> {
                val sessionId = data.string("sessionId") ?: return
                messagesBySession.update { map ->
                    val cleaned = map[sessionId].orEmpty().filterNot { it.id.startsWith("local-") }
                    map + (sessionId to cleaned)
                }
                data["userMessage"]?.let { element ->
                    runCatching { json.decodeFromJsonElement<ChatMessage>(element) }
                        .getOrNull()
                        ?.let { upsertMessage(sessionId, it) }
                }
                data["assistantMessage"]?.let { element ->
                    runCatching { json.decodeFromJsonElement<ChatMessage>(element) }
                        .getOrNull()
                        ?.let { upsertMessage(sessionId, it) }
                }
                touchSession(sessionId)
                patchRunState(sessionId, AgentRunState.Streaming)
            }
            "chat.delta", "ChatDelta" -> {
                val sessionId = data.string("sessionId") ?: return
                val messageId = data.string("messageId") ?: return
                val delta = data.string("delta").orEmpty()
                upsertAssistantDelta(sessionId, messageId, delta)
                patchRunState(sessionId, AgentRunState.Streaming)
            }
            "chat.reasoning" -> {
                val sessionId = data.string("sessionId") ?: return
                val messageId = data.string("messageId") ?: return
                val delta = data.string("content").orEmpty()
                upsertAssistantReasoningDelta(sessionId, messageId, delta)
                patchRunState(sessionId, AgentRunState.Streaming)
            }
            "chat.finished", "ChatFinished" -> {
                val sessionId = data.string("sessionId") ?: return
                val messageId = data.string("messageId") ?: return
                val content = data.string("content").orEmpty()
                val reasoning = data.string("reasoning")
                finalizeAssistant(sessionId, messageId, content, reasoning)
                patchRunState(sessionId, AgentRunState.Idle)
                // Full history carries server-computed codeChanges/planTasks per message.
                appScope.launch { loadHistory(sessionId) }
            }
            "chat.error", "ChatError" -> {
                val sessionId = data.string("sessionId") ?: return
                val messageId = data.string("messageId")
                if (messageId != null) {
                    messagesBySession.update { map ->
                        val current = map[sessionId].orEmpty().toMutableList()
                        val index = current.indexOfFirst { it.id == messageId }
                        if (index >= 0) {
                            current[index] = current[index].copy(status = MessageStatus.Error)
                        }
                        map + (sessionId to current)
                    }
                }
                patchRunState(sessionId, AgentRunState.Error)
            }
            "tool.approval", "tool-approval" -> {
                val sessionId = data.string("sessionId") ?: return
                patchRunState(sessionId, AgentRunState.WaitingApproval)
            }
            "tool.started", "tool-started", "ToolStarted" -> {
                upsertToolActivity(data, finished = false)
            }
            "tool.finished", "tool-finished", "ToolFinished" -> {
                upsertToolActivity(data, finished = true)
            }
            "AskUser", "ask.user", "ask-user" -> {
                val sessionId = data.string("sessionId") ?: return
                patchRunState(sessionId, AgentRunState.WaitingAskUser)
                // Ask interrupts generation — stop treating the assistant bubble as streaming.
                messagesBySession.update { map ->
                    val current = map[sessionId].orEmpty().toMutableList()
                    val idx = current.indexOfLast {
                        it.role == ChatRole.Assistant &&
                            (it.status == MessageStatus.Streaming || it.status == MessageStatus.Pending)
                    }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(status = MessageStatus.Complete)
                    }
                    map + (sessionId to current)
                }
            }
            "interaction.resolved", "interaction-resolved" -> {
                // Desktop or another client answered — leave WaitingAskUser / WaitingApproval.
                _sessions.update { list ->
                    list.map { session ->
                        when (session.runState) {
                            AgentRunState.WaitingAskUser,
                            AgentRunState.WaitingApproval,
                            -> session.copy(runState = AgentRunState.Idle)
                            else -> session
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun handleRpc(result: ServerMessage.RpcResult) {
        if (!result.ok) return
        val payload = result.data ?: return
        if (payload is JsonObject) {
            if (payload.containsKey("sessions") || payload.containsKey("workspaces")) {
                applySnapshot(payload)
                return
            }
            if (payload.containsKey("messages")) {
                val sessionId = payload.string("sessionId") ?: return
                val messages = runCatching {
                    json.decodeFromJsonElement<List<ChatMessage>>(payload["messages"]!!)
                }.getOrElse { emptyList() }
                messagesBySession.update { it + (sessionId to messages) }
                return
            }
            if (payload.containsKey("compose")) {
                val sessionId = payload.string("sessionId") ?: return
                val compose = runCatching {
                    json.decodeFromJsonElement<SessionCompose>(payload["compose"]!!)
                }.getOrNull() ?: return
                composeBySession.update { it + (sessionId to compose) }
                return
            }
            if (payload.containsKey("models")) {
                val models = runCatching {
                    json.decodeFromJsonElement<List<ChatModelInfo>>(payload["models"]!!)
                }.getOrElse { emptyList() }
                _models.value = models
                return
            }
        }
        runCatching {
            val list = json.decodeFromJsonElement<List<ChatSessionSummary>>(payload)
            _sessions.value = list
        }
    }

    private fun applySnapshot(data: JsonObject) {
        runCatching {
            data["sessions"]?.let { element ->
                _sessions.value = json.decodeFromJsonElement(element)
            }
            data["workspaces"]?.let { element ->
                _workspaces.value = json.decodeFromJsonElement(element)
            }
        }.onFailure { error ->
            Timber.w(error, "Failed to apply session snapshot")
        }
    }

    private fun patchRunState(sessionId: String, runState: AgentRunState) {
        _sessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) session.copy(runState = runState) else session
            }
        }
    }

    /** Bump session in the home inbox so approval / new turns surface at the top. */
    private fun touchSession(sessionId: String) {
        val now = System.currentTimeMillis()
        _sessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) session.copy(updatedAtEpochMs = now) else session
            }.sortedByDescending { it.updatedAtEpochMs }
        }
    }

    private fun upsertMessage(sessionId: String, message: ChatMessage) {
        messagesBySession.update { map ->
            val current = map[sessionId].orEmpty().toMutableList()
            val index = current.indexOfFirst { it.id == message.id }
            if (index >= 0) {
                current[index] = message
            } else {
                current += message
            }
            map + (sessionId to current.sortedBy { it.createdAtEpochMs })
        }
    }

    private fun upsertAssistantDelta(sessionId: String, messageId: String, delta: String) {
        messagesBySession.update { map ->
            val current = map[sessionId].orEmpty().toMutableList()
            val index = current.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(
                    content = old.content + delta,
                    status = MessageStatus.Streaming,
                )
            } else {
                current += ChatMessage(
                    id = messageId,
                    sessionId = sessionId,
                    role = ChatRole.Assistant,
                    content = delta,
                    status = MessageStatus.Streaming,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            }
            map + (sessionId to current)
        }
    }

    private fun upsertAssistantReasoningDelta(sessionId: String, messageId: String, delta: String) {
        messagesBySession.update { map ->
            val current = map[sessionId].orEmpty().toMutableList()
            val index = current.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(
                    reasoning = old.reasoning.orEmpty() + delta,
                    status = MessageStatus.Streaming,
                )
            } else {
                current += ChatMessage(
                    id = messageId,
                    sessionId = sessionId,
                    role = ChatRole.Assistant,
                    content = "",
                    reasoning = delta,
                    status = MessageStatus.Streaming,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            }
            map + (sessionId to current)
        }
    }

    private fun finalizeAssistant(sessionId: String, messageId: String, content: String, reasoning: String?) {
        messagesBySession.update { map ->
            val current = map[sessionId].orEmpty().toMutableList()
            val index = current.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(
                    content = content,
                    reasoning = reasoning ?: old.reasoning,
                    status = MessageStatus.Complete,
                    toolActivities = old.toolActivities.map { activity ->
                        if (activity.status == "running") {
                            activity.copy(status = if (activity.success) "done" else "error")
                        } else {
                            activity
                        }
                    },
                )
            }
            map + (sessionId to current)
        }
    }

    private fun upsertToolActivity(data: JsonObject, finished: Boolean) {
        val sessionId = data.string("sessionId") ?: return
        val messageId = data.string("messageId") ?: return
        val activityId = data.string("activityId") ?: return
        val toolName = data.string("toolName").orEmpty()
        val title = data.string("title").orEmpty().ifBlank { toolName }
        val kind = data.string("kind").orEmpty().ifBlank { "other" }
        val detail = data.string("detail")
        val result = data.string("result")
        val success = data["success"]?.jsonPrimitive?.booleanOrNull ?: true
        val status = data.string("status")
            ?: if (finished) {
                if (success) "done" else "error"
            } else {
                "running"
            }
        val arguments = data["arguments"] as? JsonObject
        val preview = data["preview"]?.let { element ->
            runCatching { json.decodeFromJsonElement<ToolPreviewPayload>(element) }.getOrNull()
        }
        val activity = ToolActivity(
            id = activityId,
            subagentId = data.string("subagentId"),
            parentActivityId = data.string("parentActivityId"),
            toolName = toolName,
            title = title,
            kind = kind,
            detail = detail,
            arguments = arguments,
            result = result,
            preview = preview,
            success = success,
            status = status,
        )

        messagesBySession.update { map ->
            val current = map[sessionId].orEmpty().toMutableList()
            val index = current.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                val old = current[index]
                val activities = old.toolActivities.toMutableList()
                val existing = activities.indexOfFirst { it.id == activityId }
                if (existing >= 0) {
                    activities[existing] = activity
                } else {
                    activities += activity
                }
                val codeChanges = if (finished && success) {
                    mergeCodeChanges(old.codeChanges, messageId, activity)
                } else {
                    old.codeChanges
                }
                current[index] = old.copy(
                    toolActivities = activities,
                    codeChanges = codeChanges,
                    status = if (old.status == MessageStatus.Complete) {
                        old.status
                    } else {
                        MessageStatus.Streaming
                    },
                )
            } else {
                current += ChatMessage(
                    id = messageId,
                    sessionId = sessionId,
                    role = ChatRole.Assistant,
                    content = "",
                    status = MessageStatus.Streaming,
                    createdAtEpochMs = System.currentTimeMillis(),
                    toolActivities = listOf(activity),
                    codeChanges = if (finished && success) {
                        mergeCodeChanges(emptyList(), messageId, activity)
                    } else {
                        emptyList()
                    },
                )
            }
            map + (sessionId to current)
        }
        patchRunState(sessionId, AgentRunState.Streaming)
    }

    private fun mergeCodeChanges(
        existing: List<CodeChangeEntry>,
        messageId: String,
        activity: ToolActivity,
    ): List<CodeChangeEntry> {
        val fromPreview = codeChangesFromActivity(messageId, activity)
        if (fromPreview.isEmpty()) return existing
        val byId = existing.associateBy { it.id }.toMutableMap()
        fromPreview.forEach { entry -> byId[entry.id] = entry }
        return byId.values.toList()
    }

    private fun codeChangesFromActivity(messageId: String, activity: ToolActivity): List<CodeChangeEntry> {
        val preview = activity.preview
        if (preview != null && preview.path.isNotBlank()) {
            val (added, removed) = countDiffLines(preview.unifiedDiff)
            val out = mutableListOf(
                CodeChangeEntry(
                    id = "$messageId:${activity.id}",
                    path = preview.path,
                    added = added,
                    removed = removed,
                ),
            )
            preview.affectedPaths
                .filter { it.isNotBlank() && it != preview.path }
                .forEach { path ->
                    out += CodeChangeEntry(
                        id = "$messageId:${activity.id}:$path",
                        path = path,
                        added = 0,
                        removed = 0,
                    )
                }
            return out
        }
        val path = activity.arguments?.string("path").orEmpty()
        if (path.isBlank()) return emptyList()
        val mutableTools = setOf(
            "write_file",
            "replace_in_file",
            "replace_many_in_file",
            "apply_patch",
        )
        if (activity.toolName !in mutableTools && activity.kind !in setOf("create", "edit", "delete", "move")) {
            return emptyList()
        }
        return listOf(
            CodeChangeEntry(
                id = "$messageId:${activity.id}",
                path = path,
                added = 0,
                removed = 0,
            ),
        )
    }

    private fun countDiffLines(diff: String): Pair<Int, Int> {
        var added = 0
        var removed = 0
        diff.lineSequence().forEach { line ->
            when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> Unit
                line.startsWith("+") -> added += 1
                line.startsWith("-") -> removed += 1
            }
        }
        return added to removed
    }

    private fun snippetAround(text: String, needle: String, radius: Int = 36): String {
        val normalized = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return ""
        val idx = normalized.indexOf(needle, ignoreCase = true)
        if (idx < 0) return normalized.take(80)
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + needle.length + radius).coerceAtMost(normalized.length)
        return buildString {
            if (start > 0) append('…')
            append(normalized.substring(start, end))
            if (end < normalized.length) append('…')
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

@Singleton
public class DefaultApprovalRepository @Inject constructor(
    private val gateway: RemoteGatewayClient,
    private val json: Json,
    @ApplicationScope private val appScope: CoroutineScope,
) : ApprovalRepository {

    private val _pending = MutableStateFlow<List<PendingApproval>>(emptyList())
    override val pending: StateFlow<List<PendingApproval>> = _pending.asStateFlow()

    init {
        appScope.launch {
            gateway.incoming.collect { message ->
                if (message !is ServerMessage.Event) return@collect
                when (message.name) {
                    "tool.approval", "tool-approval", "AskUser", "ask.user", "ask-user" -> {
                        val requestId = message.data.string("requestId") ?: return@collect
                        val sessionId = message.data.string("sessionId").orEmpty()
                        val title = message.data.string("title")
                            ?: message.data.string("toolName")
                            ?: "需要审批"
                        val kind = when (message.name) {
                            "AskUser", "ask.user", "ask-user" -> ApprovalKind.AskUser
                            else -> ApprovalKind.Tool
                        }
                        val questions = message.data["questions"]?.let { element ->
                            runCatching {
                                json.decodeFromJsonElement<List<AskUserQuestion>>(element)
                            }.getOrElse { emptyList() }
                        }.orEmpty()
                        _pending.update { list ->
                            list.filterNot { it.requestId == requestId } + PendingApproval(
                                requestId = requestId,
                                sessionId = sessionId,
                                kind = kind,
                                title = title,
                                toolName = message.data.string("toolName"),
                                previewSummary = message.data.string("preview"),
                                questions = questions,
                                createdAtEpochMs = System.currentTimeMillis(),
                            )
                        }
                    }
                    "interaction.resolved", "interaction-resolved" -> {
                        val requestId = message.data.string("requestId") ?: return@collect
                        _pending.update { list -> list.filterNot { it.requestId == requestId } }
                    }
                    else -> Unit
                }
            }
        }
    }

    override suspend fun respond(requestId: String, decision: ApprovalDecision): AnyaResult<Unit> {
        val wire = when (decision) {
            ApprovalDecision.AllowOnce -> ApprovalDecisionWire.AllowOnce
            ApprovalDecision.AllowSession -> ApprovalDecisionWire.AllowSession
            ApprovalDecision.Deny -> ApprovalDecisionWire.Deny
        }
        val result = gateway.send(
            ClientMessage.ApprovalRespond(
                requestId = UUID.randomUUID().toString(),
                approvalRequestId = requestId,
                decision = wire,
            ),
        )
        if (result is AnyaResult.Success) {
            _pending.update { list -> list.filterNot { it.requestId == requestId } }
        }
        return result
    }

    override suspend fun respondAsk(requestId: String, answer: String): AnyaResult<Unit> {
        val result = gateway.send(
            ClientMessage.AskRespond(
                requestId = UUID.randomUUID().toString(),
                askRequestId = requestId,
                answer = answer,
            ),
        )
        if (result is AnyaResult.Success) {
            _pending.update { list -> list.filterNot { it.requestId == requestId } }
        }
        return result
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

@Singleton
public class DefaultWorkspaceRepository @Inject constructor(
    private val gateway: RemoteGatewayClient,
    private val json: Json,
    private val attachCatalogStore: AttachCatalogStore,
) : WorkspaceRepository {

    private val _snapshot = MutableStateFlow<WorkspaceSnapshot?>(null)
    override val snapshot: StateFlow<WorkspaceSnapshot?> = _snapshot.asStateFlow()

    private val _filesCatalog = MutableStateFlow<WorkspaceFilesCatalog?>(null)
    override val filesCatalog: StateFlow<WorkspaceFilesCatalog?> = _filesCatalog.asStateFlow()

    private val _skills = MutableStateFlow<List<SkillSummary>>(emptyList())
    override val skills: StateFlow<List<SkillSummary>> = _skills.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<McpServerSummary>>(emptyList())
    override val mcpServers: StateFlow<List<McpServerSummary>> = _mcpServers.asStateFlow()

    override suspend fun refresh(sessionId: String?): AnyaResult<WorkspaceSnapshot> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(
                ClientMessage.WorkspaceSnapshot(requestId = requestId, sessionId = sessionId),
                requestId,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    return AnyaResult.Failure(AnyaError.Network(rpc.error ?: "workspace.snapshot failed"))
                }
                val payload = rpc.data
                val snapshot = if (payload is JsonObject) {
                    runCatching { json.decodeFromJsonElement<WorkspaceSnapshot>(payload) }
                        .getOrElse {
                            WorkspaceSnapshot(
                                workspaceId = payload.string("workspaceId"),
                                name = payload.string("name"),
                                rootPath = payload.string("rootPath"),
                                sessionId = sessionId,
                            )
                        }
                } else {
                    WorkspaceSnapshot(sessionId = sessionId)
                }
                _snapshot.value = snapshot
                AnyaResult.Success(snapshot)
            }
        }
    }

    override suspend fun readFile(path: String): AnyaResult<FileContent> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(
                ClientMessage.WorkspaceReadFile(requestId = requestId, path = path),
                requestId,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "workspace.readFile failed"))
                } else {
                    AnyaResult.Success(FileContent(path = path, content = rpc.data?.toString().orEmpty()))
                }
            }
        }
    }

    override suspend fun refreshFiles(
        sessionId: String?,
        workspaceId: String?,
    ): AnyaResult<WorkspaceFilesCatalog> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(
                ClientMessage.WorkspaceFiles(
                    requestId = requestId,
                    sessionId = sessionId,
                    workspaceId = workspaceId,
                ),
                requestId,
                timeoutMs = 30_000,
            )
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    return AnyaResult.Failure(AnyaError.Network(rpc.error ?: "workspace.files failed"))
                }
                val payload = rpc.data
                val catalog = if (payload is JsonObject) {
                    runCatching { json.decodeFromJsonElement<WorkspaceFilesCatalog>(payload) }
                        .getOrElse {
                            WorkspaceFilesCatalog(
                                workspaceId = payload.string("workspaceId"),
                                name = payload.string("name"),
                                rootPath = payload.string("rootPath"),
                                error = payload.string("error"),
                            )
                        }
                } else {
                    WorkspaceFilesCatalog(error = "empty payload")
                }
                _filesCatalog.value = catalog
                AnyaResult.Success(catalog)
            }
        }
    }

    override suspend fun refreshSkills(): AnyaResult<List<SkillSummary>> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(ClientMessage.SkillsList(requestId), requestId)
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    return AnyaResult.Failure(AnyaError.Network(rpc.error ?: "skills.list failed"))
                }
                val payload = rpc.data
                val skills = if (payload is JsonObject) {
                    runCatching {
                        json.decodeFromJsonElement<List<SkillSummary>>(payload["skills"]!!)
                    }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
                _skills.value = skills
                AnyaResult.Success(skills)
            }
        }
    }

    override suspend fun refreshMcpServers(): AnyaResult<List<McpServerSummary>> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(ClientMessage.McpList(requestId), requestId)
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    return AnyaResult.Failure(AnyaError.Network(rpc.error ?: "mcp.list failed"))
                }
                val payload = rpc.data
                val servers = if (payload is JsonObject) {
                    runCatching {
                        json.decodeFromJsonElement<List<McpServerSummary>>(payload["mcpServers"]!!)
                    }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
                _mcpServers.value = servers
                AnyaResult.Success(servers)
            }
        }
    }

    override suspend fun loadCachedAttachCatalog(): Pair<List<SkillSummary>, List<McpServerSummary>> {
        val cached = attachCatalogStore.load()
        if (cached.skills.isNotEmpty()) _skills.value = cached.skills
        if (cached.mcpServers.isNotEmpty()) _mcpServers.value = cached.mcpServers
        return cached.skills to cached.mcpServers
    }

    override suspend fun persistAttachCatalog(
        skills: List<SkillSummary>,
        mcpServers: List<McpServerSummary>,
    ): Pair<List<SkillSummary>, List<McpServerSummary>> {
        attachCatalogStore.save(skills, mcpServers)
        val cached = attachCatalogStore.load()
        if (cached.skills.isNotEmpty()) _skills.value = cached.skills
        if (cached.mcpServers.isNotEmpty()) _mcpServers.value = cached.mcpServers
        return cached.skills to cached.mcpServers
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

