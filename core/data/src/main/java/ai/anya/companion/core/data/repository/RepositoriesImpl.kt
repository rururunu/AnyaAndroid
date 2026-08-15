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
import ai.anya.companion.core.data.local.InboxResultStore
import ai.anya.companion.core.model.inbox.InboxResultKind
import ai.anya.companion.core.model.inbox.InboxResultRecord
import ai.anya.companion.core.data.local.CredentialStore
import ai.anya.companion.core.model.approval.ApprovalDecision
import ai.anya.companion.core.model.approval.ApprovalKind
import ai.anya.companion.core.model.approval.AskUserQuestion
import ai.anya.companion.core.model.approval.PendingApproval
import ai.anya.companion.core.model.protocol.ApprovalDecisionWire
import ai.anya.companion.core.model.protocol.ClientMessage
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.core.model.protocol.HostDisplayName
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
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.ChatSharedUrl
import ai.anya.companion.core.model.session.SharedFileStatus
import ai.anya.companion.core.model.session.wireValue
import ai.anya.companion.core.model.workspace.CompanionFileOffer
import ai.anya.companion.core.model.workspace.CompanionUrlOffer
import ai.anya.companion.core.model.workspace.DownloadedWorkspaceFile
import ai.anya.companion.core.model.workspace.UploadedCompanionFile
import ai.anya.companion.core.model.workspace.FileContent
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import ai.anya.companion.core.model.workspace.WorkspaceFilesCatalog
import ai.anya.companion.core.model.workspace.WorkspaceSnapshot
import ai.anya.companion.core.model.workspace.WorkspaceSummary
import ai.anya.companion.core.model.workspace.downloadPathCandidates
import ai.anya.companion.core.model.workspace.normalizeSharedFilePath
import ai.anya.companion.core.network.gateway.GatewaySocketState
import ai.anya.companion.core.network.gateway.RemoteGatewayClient
import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class DefaultConnectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialStore: CredentialStore,
    private val gateway: RemoteGatewayClient,
    @ApplicationScope private val appScope: CoroutineScope,
) : ConnectionRepository {

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _credential = MutableStateFlow<DeviceCredential?>(null)
    override val credential: StateFlow<DeviceCredential?> = _credential.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<DeviceCredential>>(emptyList())
    override val pairedDevices: StateFlow<List<DeviceCredential>> = _pairedDevices.asStateFlow()

    /** When true, keep trying to stay online whenever a credential exists. */
    private val wantConnected = MutableStateFlow(true)
    private val connectMutex = Mutex()
    private var reconnectJob: Job? = null
    private val lastAttempt = java.util.concurrent.atomic.AtomicReference<DeviceCredential?>(null)
    private val lastGoodEndpoint = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /** Epoch ms of the last server app-level ping (or hello.ok). */
    private val lastServerAliveAt = java.util.concurrent.atomic.AtomicLong(0L)
    /** True after hello.ok in this process. Cold-start failures must not retry forever. */
    private val sessionReachedConnected = java.util.concurrent.atomic.AtomicBoolean(false)

    private companion object {
        /** No server ping within this window ⇒ treat the socket as half-open. */
        const val STALE_DEADLINE_MS: Long = 45_000L
        /** Phone → desktop keep-alive so proxies see bidirectional traffic. */
        const val CLIENT_HEARTBEAT_MS: Long = 20_000L
        /** How often to check for a stale link. */
        const val STALE_POLL_MS: Long = 5_000L
        /** Desktop hello window is 20s; wait a bit less so we can still fallback. */
        const val HELLO_TIMEOUT_MS: Long = 15_000L
    }

    init {
        appScope.launch {
            credentialStore.rosterFlow.collect { roster ->
                val previousId = _credential.value?.deviceId
                val saved = roster.active()
                _pairedDevices.value = roster.devices
                _credential.value = saved
                saved?.lastGoodEndpointKey()?.let { lastGoodEndpoint.compareAndSet(null, it) }
                if (saved == null) {
                    wantConnected.value = true
                    sessionReachedConnected.set(false)
                    lastGoodEndpoint.set(null)
                    lastAttempt.set(null)
                    reconnectJob?.cancel()
                    gateway.disconnect()
                    _connectionState.value = ConnectionState.Disconnected
                } else if (saved.deviceId != previousId) {
                    lastGoodEndpoint.set(saved.lastGoodEndpointKey())
                    lastAttempt.set(null)
                    if (previousId != null) {
                        sessionReachedConnected.set(false)
                        reconnectJob?.cancel()
                        gateway.disconnect()
                    }
                    if (wantConnected.value) {
                        ensureConnected(saved)
                    }
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
                        if (shouldKeepRetrying()) {
                            scheduleReconnect()
                        } else {
                            reconnectJob?.cancel()
                        }
                    }
                    GatewaySocketState.Idle,
                    GatewaySocketState.Closed,
                    GatewaySocketState.Closing,
                    -> {
                        if (shouldKeepRetrying()) {
                            if (_connectionState.value == ConnectionState.Connected) {
                                _connectionState.value = ConnectionState.Reconnecting
                            }
                            scheduleReconnect()
                        } else if (!wantConnected.value) {
                            _connectionState.value = ConnectionState.Disconnected
                        } else {
                            reconnectJob?.cancel()
                            _connectionState.value = ConnectionState.Error
                        }
                    }
                }
            }
        }
        appScope.launch {
            gateway.incoming.collect { message ->
                when (message) {
                    is ServerMessage.HelloOk -> {
                        lastServerAliveAt.set(System.currentTimeMillis())
                        sessionReachedConnected.set(true)
                        reconnectJob?.cancel()
                        _connectionState.value = ConnectionState.Connected
                        lastAttempt.get()?.let { rememberLastGood(it) }
                    }
                    is ServerMessage.HelloError -> {
                        _connectionState.value = ConnectionState.Error
                        wantConnected.value = false
                        gateway.disconnect()
                    }
                    is ServerMessage.Ping -> {
                        lastServerAliveAt.set(System.currentTimeMillis())
                        // Bidirectional keep-alive for Cloudflare / NAT (WS pings alone often die).
                        gateway.send(ClientMessage.Pong(message.ts))
                    }
                    else -> Unit
                }
            }
        }
        registerNetworkCallback()
        startClientHeartbeat()
        startStaleWatchdog()
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
                        // Handshake / LAN→public fallback can take >15s. Only recycle a
                        // socket that opened but never finished hello.
                        if (gateway.state.value != GatewaySocketState.Open) return@launch
                        Timber.w("Connection stuck in %s for 15s with socket open; forcing a clean reconnect", current)
                        gateway.disconnect()
                        reconnectJob?.cancel()
                        if (shouldKeepRetrying()) {
                            scheduleReconnect()
                        } else {
                            _connectionState.value = ConnectionState.Error
                        }
                    }
                }
            }
        }
    }

    /** Phone-initiated keep-alive: proxies that only forward client→server traffic stay warm. */
    private fun startClientHeartbeat() {
        appScope.launch {
            while (isActive) {
                delay(CLIENT_HEARTBEAT_MS)
                if (!wantConnected.value) continue
                if (_connectionState.value != ConnectionState.Connected) continue
                if (gateway.state.value != GatewaySocketState.Open) continue
                gateway.send(ClientMessage.Pong(System.currentTimeMillis()))
            }
        }
    }

    /**
     * If the desktop's 15s app-level pings stop arriving, the socket is almost certainly
     * half-open (carrier NAT / tunnel blip). Recycle immediately instead of waiting for
     * the next failed send.
     */
    private fun startStaleWatchdog() {
        appScope.launch {
            while (isActive) {
                delay(STALE_POLL_MS)
                if (!wantConnected.value) continue
                if (_connectionState.value != ConnectionState.Connected) continue
                if (gateway.state.value != GatewaySocketState.Open) continue
                val last = lastServerAliveAt.get()
                if (last == 0L) continue
                val idle = System.currentTimeMillis() - last
                if (idle < STALE_DEADLINE_MS) continue
                Timber.w("No server ping for %dms; recycling gateway socket", idle)
                gateway.disconnect()
                reconnectJob?.cancel()
                scheduleReconnect()
            }
        }
    }

    override suspend fun pair(
        payload: PairingPayload,
        replaceDeviceId: String?,
    ): AnyaResult<DeviceCredential> {
        val existing = matchExistingDevice(_pairedDevices.value, payload, replaceDeviceId)
        val displayName = HostDisplayName.orFallback(
            payload.displayName ?: existing?.displayName ?: HostDisplayName.suggest(
                host = payload.host,
                lanHost = payload.lanHost,
                deviceName = payload.deviceName,
                existing = existing?.displayName,
            ),
        )
        val credential = DeviceCredential(
            deviceId = existing?.deviceId ?: UUID.randomUUID().toString(),
            credential = payload.pairingToken,
            host = payload.host,
            port = payload.port,
            scheme = payload.scheme,
            pairedAtEpochMs = existing?.pairedAtEpochMs ?: System.currentTimeMillis(),
            lanHost = payload.lanHost ?: existing?.lanHost,
            lanPort = payload.lanPort ?: existing?.lanPort,
            displayName = displayName,
        )
        sessionReachedConnected.set(false)
        lastGoodEndpoint.set(null)
        lastAttempt.set(null)
        reconnectJob?.cancel()
        gateway.disconnect()
        wantConnected.value = true
        credentialStore.upsert(credential, makeActive = true)
        _credential.value = credential
        _pairedDevices.update { list ->
            list.filterNot { it.deviceId == credential.deviceId } + credential
        }
        ensureConnected(credential)
        return AnyaResult.Success(credential)
    }

    override suspend fun switchDevice(deviceId: String): AnyaResult<Unit> {
        val target = _pairedDevices.value.find { it.deviceId == deviceId }
            ?: return AnyaResult.Failure(AnyaError.NotPaired())
        if (target.deviceId == _credential.value?.deviceId) {
            return connect()
        }
        wantConnected.value = true
        sessionReachedConnected.set(false)
        lastGoodEndpoint.set(target.lastGoodEndpointKey())
        lastAttempt.set(null)
        reconnectJob?.cancel()
        gateway.disconnect()
        _connectionState.value = ConnectionState.Connecting
        _credential.value = target
        credentialStore.setActive(deviceId)
        return ensureConnected(target)
    }

    override suspend fun renameDevice(deviceId: String, displayName: String) {
        val current = _pairedDevices.value.find { it.deviceId == deviceId } ?: return
        val name = HostDisplayName.orFallback(displayName)
        if (current.displayName == name) return
        val updated = current.copy(displayName = name)
        credentialStore.upsert(
            updated,
            makeActive = current.deviceId == _credential.value?.deviceId,
        )
        _pairedDevices.update { list ->
            list.map { if (it.deviceId == deviceId) updated else it }
        }
        if (_credential.value?.deviceId == deviceId) {
            _credential.value = updated
        }
    }

    override suspend fun removeDevice(deviceId: String) {
        val wasActive = _credential.value?.deviceId == deviceId
        credentialStore.remove(deviceId)
        _pairedDevices.update { list -> list.filterNot { it.deviceId == deviceId } }
        if (!wasActive) return
        sessionReachedConnected.set(false)
        lastGoodEndpoint.set(null)
        lastAttempt.set(null)
        reconnectJob?.cancel()
        gateway.disconnect()
        val next = _pairedDevices.value.maxByOrNull { it.pairedAtEpochMs }
        _credential.value = next
        if (next == null) {
            wantConnected.value = true
            _connectionState.value = ConnectionState.Disconnected
        } else {
            wantConnected.value = true
            _connectionState.value = ConnectionState.Connecting
            ensureConnected(next)
        }
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
        val activeId = _credential.value?.deviceId
        if (activeId != null) {
            removeDevice(activeId)
        } else {
            disconnect()
            credentialStore.clear()
            _credential.value = null
            _pairedDevices.value = emptyList()
        }
    }

    override fun nudge() {
        if (!wantConnected.value || _credential.value == null) return
        if (isSocketHealthy() || isHandshakeInProgress()) return
        reconnectJob?.cancel()
        scheduleReconnect()
    }

    override fun abandonUnreachableBoot() {
        if (sessionReachedConnected.get()) return
        reconnectJob?.cancel()
        gateway.disconnect()
        _connectionState.value = ConnectionState.Error
    }

    private fun shouldKeepRetrying(): Boolean =
        wantConnected.value &&
            _credential.value != null &&
            sessionReachedConnected.get()

    /**
     * Reconnect immediately on real default-network transitions (Wi-Fi ↔ cellular).
     * OEM devices often emit a new [android.net.Network] handle on resume without
     * changing transport; tearing down a live Cloudflare socket for that is what
     * made the link drop for a moment after leaving the app.
     */
    private fun registerNetworkCallback() {
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
        val lastNetwork = java.util.concurrent.atomic.AtomicReference<android.net.Network?>(null)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val previous = lastNetwork.getAndSet(network)
                if (!wantConnected.value || _credential.value == null) return
                val pathChanged = previous != null &&
                    !sameDefaultPath(connectivity, previous, network)
                if (pathChanged) {
                    Timber.i("Default network path changed; recycling gateway socket")
                    gateway.disconnect()
                    reconnectJob?.cancel()
                    scheduleReconnect()
                    return
                }
                if (isSocketHealthy() || isHandshakeInProgress()) return
                reconnectJob?.cancel()
                scheduleReconnect()
            }

            override fun onLost(network: android.net.Network) {
                lastNetwork.compareAndSet(network, null)
                // Don't flip UI to Reconnecting: OEMs fire onLost when backgrounding
                // even though the socket is still alive. Closed/Failed + stale watchdog
                // cover a real drop.
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure { Timber.w(it, "registerDefaultNetworkCallback failed") }
    }

    private fun isSocketHealthy(): Boolean =
        _connectionState.value == ConnectionState.Connected &&
            gateway.state.value == GatewaySocketState.Open

    private fun isHandshakeInProgress(): Boolean {
        val socket = gateway.state.value
        val conn = _connectionState.value
        return socket == GatewaySocketState.Connecting ||
            (socket == GatewaySocketState.Open &&
                (conn == ConnectionState.Connecting || conn == ConnectionState.Reconnecting))
    }

    private fun sameDefaultPath(
        connectivity: ConnectivityManager,
        previous: android.net.Network,
        current: android.net.Network,
    ): Boolean {
        if (previous == current) return true
        val prevCaps = connectivity.getNetworkCapabilities(previous) ?: return false
        val currCaps = connectivity.getNetworkCapabilities(current) ?: return false
        if (!currCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return defaultTransports(prevCaps) == defaultTransports(currCaps)
    }

    private fun defaultTransports(caps: NetworkCapabilities): Set<Int> = buildSet {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            add(NetworkCapabilities.TRANSPORT_WIFI)
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            add(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            add(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            add(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private fun scheduleReconnect() {
        if (!shouldKeepRetrying()) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = appScope.launch {
            var attempt = 0
            while (isActive && shouldKeepRetrying()) {
                val credential = _credential.value ?: return@launch
                if (isSocketHealthy()) return@launch
                if (isHandshakeInProgress()) {
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
                if (!shouldKeepRetrying()) return@launch
                if (isSocketHealthy()) return@launch
                if (isHandshakeInProgress()) continue
                ensureConnected(credential)
                if (isSocketHealthy()) return@launch
            }
            if (!sessionReachedConnected.get() && wantConnected.value) {
                _connectionState.value = ConnectionState.Error
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
                    openWithFallback(credential)
                }
            }
        }

    /**
     * Try last-good endpoint first, then LAN (when on Wi-Fi), then the public host.
     * Same-Wi-Fi phones skip Cloudflare entirely — much more stable in China.
     */
    private suspend fun openWithFallback(credential: DeviceCredential): AnyaResult<Unit> {
        val candidates = orderedCandidates(credential)
        if (candidates.isEmpty()) {
            _connectionState.value = ConnectionState.Error
            return AnyaResult.Failure(AnyaError.Network("No reachable gateway endpoint"))
        }
        var lastFailure: AnyaResult.Failure? = null
        for ((index, candidate) in candidates.withIndex()) {
            Timber.i(
                "Gateway connect attempt %d/%d → %s://%s:%d",
                index + 1,
                candidates.size,
                candidate.scheme,
                candidate.host,
                candidate.port,
            )
            lastAttempt.set(candidate)
            val timeoutMs = connectTimeoutMs(credential, candidate, candidates.size)
            val handshakeTries = 1
            var openedOk = false
            handshake@ for (tryIndex in 0 until handshakeTries) {
                val opened = gateway.connect(candidate, timeoutMs)
                if (opened is AnyaResult.Failure) {
                    lastFailure = opened
                    continue
                }
                val startedAt = System.currentTimeMillis()
                when (val ready = waitForOpenOrFail(timeoutMs)) {
                    is AnyaResult.Success -> {
                        openedOk = true
                        break@handshake
                    }
                    is AnyaResult.Failure -> {
                        lastFailure = ready
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (tryIndex < handshakeTries - 1 && elapsed < 4_000L) {
                            Timber.w(
                                "Public gateway handshake dropped in %dms; retrying same endpoint",
                                elapsed,
                            )
                            delay(250)
                        }
                    }
                }
            }
            if (!openedOk) continue
            // Always hello with the stored credential identity (deviceId / token);
            // only the transport endpoint differs per candidate.
            when (val hello = sendHello(credential)) {
                is AnyaResult.Success -> return hello
                is AnyaResult.Failure -> {
                    lastFailure = hello
                    if (!wantConnected.value) break
                    Timber.w(
                        "hello failed on %s://%s:%d — trying next candidate",
                        candidate.scheme,
                        candidate.host,
                        candidate.port,
                    )
                    gateway.disconnect()
                }
            }
        }
        _connectionState.value = ConnectionState.Error
        return lastFailure ?: AnyaResult.Failure(AnyaError.Network("Gateway connect failed"))
    }

    private fun matchExistingDevice(
        devices: List<DeviceCredential>,
        payload: PairingPayload,
        replaceDeviceId: String?,
    ): DeviceCredential? {
        if (!replaceDeviceId.isNullOrBlank()) {
            devices.find { it.deviceId == replaceDeviceId }?.let { return it }
        }
        val lan = payload.lanHost?.trim()?.takeIf { it.isNotEmpty() }
        if (lan != null) {
            devices.find { it.lanHost?.trim().equals(lan, ignoreCase = true) }?.let { return it }
        }
        val host = payload.host.trim()
        return devices.find { it.host.equals(host, ignoreCase = true) }
    }

    private fun orderedCandidates(credential: DeviceCredential): List<DeviceCredential> {
        var list = credential.connectCandidates()
        if (!shouldProbeLan()) {
            val lan = credential.lanHost?.trim().orEmpty()
            if (lan.isNotEmpty()) {
                list = list.filterNot { it.host == lan && it.scheme == "ws" }
            }
        }
        val prefer = lastGoodEndpoint.get() ?: credential.lastGoodEndpointKey()
        if (prefer != null) {
            val sticky = list.filter { it.endpointKey() == prefer }
            val rest = list.filter { it.endpointKey() != prefer }
            list = sticky + rest
        }
        return list
    }

    private fun shouldProbeLan(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun connectTimeoutMs(
        credential: DeviceCredential,
        candidate: DeviceCredential,
        candidateCount: Int,
    ): Long {
        val lan = credential.lanHost?.trim().orEmpty()
        val lanProbe = candidateCount > 1 &&
            candidate.scheme == "ws" &&
            lan.isNotEmpty() &&
            candidate.host == lan
        return if (lanProbe) {
            RemoteGatewayClient.LAN_CONNECT_TIMEOUT_MS
        } else {
            RemoteGatewayClient.CONNECT_TIMEOUT_MS
        }
    }

    private fun rememberLastGood(candidate: DeviceCredential) {
        lastGoodEndpoint.set(candidate.endpointKey())
        val current = _credential.value ?: return
        if (current.lastGoodHost == candidate.host &&
            current.lastGoodPort == candidate.port &&
            current.lastGoodScheme == candidate.scheme
        ) {
            return
        }
        val updated = current.copy(
            lastGoodHost = candidate.host,
            lastGoodPort = candidate.port,
            lastGoodScheme = candidate.scheme,
        )
        _credential.value = updated
        _pairedDevices.update { list ->
            list.map { if (it.deviceId == updated.deviceId) updated else it }
        }
        appScope.launch { credentialStore.upsert(updated, makeActive = true) }
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
                appVersion = "0.1.2",
            ),
        )
        if (sent is AnyaResult.Failure) {
            _connectionState.value = ConnectionState.Error
            return sent
        }
        // hello.ok flips Connected via the incoming collector; wait briefly so callers
        // that refresh right after connect() see an authenticated session.
        val authed = withTimeoutOrNull(HELLO_TIMEOUT_MS) {
            connectionState.first { it == ConnectionState.Connected || it == ConnectionState.Error }
        }
        return when (authed) {
            ConnectionState.Connected -> AnyaResult.Success(Unit)
            ConnectionState.Error -> {
                val socket = gateway.state.value
                if (socket == GatewaySocketState.Failed || socket == GatewaySocketState.Closed) {
                    AnyaResult.Failure(AnyaError.Network("connection dropped during hello"))
                } else {
                    AnyaResult.Failure(AnyaError.Unauthorized("hello rejected"))
                }
            }
            else -> AnyaResult.Failure(AnyaError.Network("hello timed out"))
        }
    }

    private suspend fun waitForOpenOrFail(
        timeoutMs: Long = RemoteGatewayClient.CONNECT_TIMEOUT_MS,
    ): AnyaResult<Unit> {
        val terminal = withTimeoutOrNull(timeoutMs) {
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
                AnyaResult.Failure(AnyaError.Network("连接超时，已自动断开并重试"))
            }
            else -> AnyaResult.Failure(AnyaError.Network("Gateway connect failed"))
        }
    }
}

@Singleton
public class DefaultSessionRepository @Inject constructor(
    private val gateway: RemoteGatewayClient,
    private val json: Json,
    private val connectionRepository: ConnectionRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val inboxResultStore: InboxResultStore,
) : SessionRepository {

    private val _sessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    override val sessions: StateFlow<List<ChatSessionSummary>> = _sessions.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceSummary>>(emptyList())
    override val workspaces: StateFlow<List<WorkspaceSummary>> = _workspaces.asStateFlow()

    private val _fileOffers = MutableSharedFlow<CompanionFileOffer>(extraBufferCapacity = 8)
    override val fileOffers: SharedFlow<CompanionFileOffer> = _fileOffers.asSharedFlow()
    private val _urlOffers = MutableSharedFlow<CompanionUrlOffer>(extraBufferCapacity = 8)
    override val urlOffers: SharedFlow<CompanionUrlOffer> = _urlOffers.asSharedFlow()
    override val inboxResults: StateFlow<List<InboxResultRecord>> = combine(
        inboxResultStore.records,
        connectionRepository.credential,
    ) { records, cred ->
        val id = cred?.deviceId.orEmpty()
        if (id.isBlank()) records else records.filter { it.deviceId.isBlank() || it.deviceId == id }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    private val messagesBySession = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    /** Companion-only shared-file cards; survive desktop history reloads. */
    private val localSharedBySession = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val composeBySession = MutableStateFlow<Map<String, SessionCompose>>(emptyMap())
    private val tasksBySession = MutableStateFlow<Map<String, List<PlanTaskItem>>>(emptyMap())
    private val _models = MutableStateFlow<List<ChatModelInfo>>(emptyList())

    init {
        appScope.launch {
            var seen = false
            var lastId: String? = null
            connectionRepository.credential
                .map { it?.deviceId }
                .distinctUntilChanged()
                .collect { id ->
                    if (seen && lastId != id) {
                        resetLocalProjection()
                    }
                    seen = true
                    lastId = id
                }
        }
        appScope.launch {
            gateway.incoming.collect { message ->
                when (message) {
                    is ServerMessage.HelloOk -> {
                        // Desktop also pushes session.snapshot after hello; pull explicitly
                        // so a missed event still fills the home list after cold start.
                        appScope.launch {
                            refreshSessions()
                            refreshModels()
                            settleIdleStreaming()
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
        combine(messagesBySession, localSharedBySession) { remote, local ->
            mergeRemoteAndLocal(remote[sessionId].orEmpty(), local[sessionId].orEmpty())
        }

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
                        }.onFailure { error ->
                            Timber.w(error, "session.history decode failed for %s", sessionId)
                        }.getOrNull()
                    }
                    if (messages != null) {
                        val runState = _sessions.value.find { it.id == sessionId }?.runState
                        messagesBySession.update { map ->
                            val local = map[sessionId].orEmpty()
                            map + (sessionId to mergeHistory(local, messages, runState))
                        }
                        hydrateSharedCardsFromHistory(sessionId, messages)
                        AnyaResult.Success(messagesBySession.value[sessionId].orEmpty())
                    } else {
                        AnyaResult.Success(messagesBySession.value[sessionId].orEmpty())
                    }
                }
            }
        }
    }

    override suspend fun deleteSession(sessionId: String): AnyaResult<Unit> {
        val requestId = UUID.randomUUID().toString()
        return when (
            val result = gateway.request(ClientMessage.SessionDelete(requestId, sessionId), requestId)
        ) {
            is AnyaResult.Failure -> result
            is AnyaResult.Success -> {
                val rpc = result.data
                if (!rpc.ok) {
                    AnyaResult.Failure(AnyaError.Network(rpc.error ?: "session.delete failed"))
                } else {
                    // Desktop broadcasts a fresh snapshot too; drop local state now
                    // so the row disappears without waiting for the round trip.
                    _sessions.update { list -> list.filterNot { it.id == sessionId } }
                    messagesBySession.update { it - sessionId }
                    localSharedBySession.update { it - sessionId }
                    composeBySession.update { it - sessionId }
                    tasksBySession.update { it - sessionId }
                    inboxResultStore.removeSessions(setOf(sessionId))
                    AnyaResult.Success(Unit)
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
        workspaceId: String?,
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
        val clientMessage = ClientMessage.ChatSend(
            requestId = requestId,
            sessionId = sessionId,
            message = message,
            workspaceId = workspaceId,
            chatMode = chatMode?.wireValue(),
            toolApprovalMode = toolApprovalMode?.wireValue(),
            chatModel = chatModel,
            chatModelProvider = chatModelProvider,
        )
        if (targetSession.isBlank()) {
            // Brand-new session: wait for the RPC so we learn the desktop-assigned id.
            return when (val result = gateway.request(clientMessage, requestId, timeoutMs = 30_000)) {
                is AnyaResult.Failure -> result
                is AnyaResult.Success -> {
                    val rpc = result.data
                    val newSessionId = (rpc.data as? JsonObject)?.string("sessionId")
                    when {
                        !rpc.ok -> AnyaResult.Failure(AnyaError.Network(rpc.error ?: "chat.send failed"))
                        newSessionId.isNullOrBlank() ->
                            AnyaResult.Failure(AnyaError.Protocol("chat.send returned no sessionId"))
                        else -> AnyaResult.Success(newSessionId)
                    }
                }
            }
        }
        return when (val send = gateway.send(clientMessage)) {
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

    override fun upsertLocalSharedMessage(message: ChatMessage) {
        val sessionId = message.sessionId
        if (sessionId.isBlank()) return
        localSharedBySession.update { map ->
            val current = map[sessionId].orEmpty().toMutableList()
            val idx = current.indexOfFirst { it.id == message.id }
            if (idx >= 0) {
                current[idx] = mergeSharedMessagePreserveDownloads(current[idx], message)
            } else {
                current.add(message)
            }
            map + (sessionId to current)
        }
        captureInboxResults(message)
    }

    override fun patchLocalSharedFile(
        sessionId: String,
        offerId: String,
        transform: (ChatSharedFile) -> ChatSharedFile,
    ) {
        localSharedBySession.update { map ->
            val current = map[sessionId].orEmpty()
            if (current.isEmpty()) return@update map
            val next = current.map { message ->
                if (message.sharedFiles.none { it.offerId == offerId }) {
                    message
                } else {
                    message.copy(
                        sharedFiles = message.sharedFiles.map { file ->
                            if (file.offerId == offerId) transform(file) else file
                        },
                    )
                }
            }
            map + (sessionId to next)
        }
        val updated = localSharedBySession.value[sessionId].orEmpty()
            .asSequence()
            .flatMap { it.sharedFiles.asSequence() }
            .firstOrNull { it.offerId == offerId }
        if (updated != null) {
            inboxResultStore.patchFile(offerId, updated)
        }
    }

    override fun markInboxUrlViewed(offerId: String) {
        inboxResultStore.markUrlViewed(offerId)
    }

    override fun deleteInboxResult(offerId: String) {
        inboxResultStore.delete(offerId)
    }

    private fun mergeRemoteAndLocal(
        remote: List<ChatMessage>,
        local: List<ChatMessage>,
    ): List<ChatMessage> {
        if (local.isEmpty()) return remote
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val extras = local.filter { it.id !in remoteIds }
        if (extras.isEmpty()) return remote
        return (remote + extras).sortedBy { it.createdAtEpochMs }
    }

    private fun mergeSharedMessagePreserveDownloads(
        previous: ChatMessage,
        incoming: ChatMessage,
    ): ChatMessage {
        val previousFiles = previous.sharedFiles.associateBy { it.offerId }
        val files = incoming.sharedFiles.map { file ->
            val old = previousFiles[file.offerId] ?: return@map file
            val keepLocal = old.localPath != null ||
                old.status == SharedFileStatus.Ready ||
                old.status == SharedFileStatus.Pending
            if (!keepLocal) {
                file
            } else {
                old.copy(
                    path = file.path.ifBlank { old.path },
                    name = file.name.ifBlank { old.name },
                    mime = file.mime.takeIf {
                        it.isNotBlank() && it != "application/octet-stream"
                    } ?: old.mime,
                    size = if (file.size > 0L) file.size else old.size,
                    workspaceId = file.workspaceId ?: old.workspaceId,
                )
            }
        }.ifEmpty { previous.sharedFiles }
        val urls = incoming.sharedUrls.ifEmpty { previous.sharedUrls }
        return incoming.copy(sharedFiles = files, sharedUrls = urls)
    }

    private fun captureInboxResults(message: ChatMessage) {
        if (message.sharedFiles.isEmpty() && message.sharedUrls.isEmpty()) return
        val session = _sessions.value.find { it.id == message.sessionId }
        val createdAt = message.createdAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val title = session?.title?.takeIf { it.isNotBlank() }
        val workspace = session?.workspaceName?.takeIf { it.isNotBlank() }
        for (file in message.sharedFiles) {
            if (file.offerId.isBlank()) continue
            inboxResultStore.upsert(
                InboxResultRecord(
                    id = file.offerId,
                    kind = InboxResultKind.File,
                    sessionId = message.sessionId,
                    sessionTitle = title,
                    workspaceName = workspace,
                    createdAtEpochMs = createdAt,
                    name = file.name,
                    path = file.path,
                    mime = file.mime,
                    size = file.size,
                    fileStatus = file.status,
                    localPath = file.localPath,
                    deviceId = currentDeviceId(),
                ),
            )
        }
        for (url in message.sharedUrls) {
            if (url.offerId.isBlank()) continue
            inboxResultStore.upsert(
                InboxResultRecord(
                    id = url.offerId,
                    kind = InboxResultKind.Url,
                    sessionId = message.sessionId,
                    sessionTitle = title,
                    workspaceName = workspace,
                    createdAtEpochMs = createdAt,
                    name = url.label.ifBlank { "Preview" },
                    publicUrl = url.publicUrl,
                    originUrl = url.originUrl,
                    deviceId = currentDeviceId(),
                ),
            )
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
            "file.offer", "file-offer", "FileOffer" -> {
                val sessionId = data.string("sessionId") ?: return
                val path = data.string("path")?.let(::normalizeSharedFilePath).orEmpty()
                if (path.isEmpty()) return
                val offer = CompanionFileOffer(
                    sessionId = sessionId,
                    offerId = data.string("offerId").orEmpty(),
                    path = path,
                    name = data.string("name")
                        ?: path.replace('\\', '/').substringAfterLast('/'),
                    mime = data.string("mime"),
                    size = data["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                    workspaceId = data.string("workspaceId"),
                )
                _fileOffers.tryEmit(offer)
                val session = _sessions.value.find { it.id == sessionId }
                inboxResultStore.upsert(
                    InboxResultRecord(
                        id = offer.offerId.ifBlank { return },
                        kind = InboxResultKind.File,
                        sessionId = sessionId,
                        sessionTitle = session?.title?.takeIf { it.isNotBlank() },
                        workspaceName = session?.workspaceName?.takeIf { it.isNotBlank() },
                        createdAtEpochMs = System.currentTimeMillis(),
                        name = offer.name,
                        path = offer.path,
                        mime = offer.mime.orEmpty(),
                        size = offer.size,
                        fileStatus = SharedFileStatus.Offered,
                        deviceId = currentDeviceId(),
                    ),
                )
            }
            "url.offer", "url-offer", "UrlOffer" -> {
                val sessionId = data.string("sessionId") ?: return
                val publicUrl = data.string("publicUrl") ?: return
                val offer = CompanionUrlOffer(
                    sessionId = sessionId,
                    offerId = data.string("offerId").orEmpty(),
                    label = data.string("label").orEmpty().ifBlank { "Preview" },
                    publicUrl = publicUrl,
                    originUrl = data.string("originUrl").orEmpty(),
                )
                _urlOffers.tryEmit(offer)
                val session = _sessions.value.find { it.id == sessionId }
                inboxResultStore.upsert(
                    InboxResultRecord(
                        id = offer.offerId.ifBlank { return },
                        kind = InboxResultKind.Url,
                        sessionId = sessionId,
                        sessionTitle = session?.title?.takeIf { it.isNotBlank() },
                        workspaceName = session?.workspaceName?.takeIf { it.isNotBlank() },
                        createdAtEpochMs = System.currentTimeMillis(),
                        name = offer.label,
                        publicUrl = offer.publicUrl,
                        originUrl = offer.originUrl,
                        deviceId = currentDeviceId(),
                    ),
                )
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
                val incoming = runCatching {
                    json.decodeFromJsonElement<List<ChatMessage>>(payload["messages"]!!)
                }.getOrElse { error ->
                    Timber.w(error, "Failed to decode session history for %s", sessionId)
                    return
                }
                val runState = _sessions.value.find { it.id == sessionId }?.runState
                messagesBySession.update { map ->
                    val local = map[sessionId].orEmpty()
                    map + (sessionId to mergeHistory(local, incoming, runState))
                }
                hydrateSharedCardsFromHistory(sessionId, incoming)
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
            val previousIds = _sessions.value.map { it.id }.toSet()
            _sessions.value = list
            if (previousIds.isNotEmpty()) {
                inboxResultStore.removeSessions(previousIds - list.map { it.id }.toSet())
            }
        }
    }

    private fun applySnapshot(data: JsonObject) {
        val previousIds = _sessions.value.map { it.id }.toSet()
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
        if (previousIds.isNotEmpty()) {
            val nextIds = _sessions.value.map { it.id }.toSet()
            inboxResultStore.removeSessions(previousIds - nextIds)
        }
        settleIdleStreaming()
    }

    /** Desktop runState is Idle/Error: local Streaming bubbles were missed `chat.finished`. */
    private fun settleIdleStreaming() {
        val byId = _sessions.value.associateBy { it.id }
        messagesBySession.update { map ->
            map.mapValues { (sessionId, messages) ->
                val runState = byId[sessionId]?.runState ?: return@mapValues messages
                if (runState == AgentRunState.Streaming ||
                    runState == AgentRunState.WaitingApproval ||
                    runState == AgentRunState.WaitingAskUser
                ) {
                    return@mapValues messages
                }
                val terminal = if (runState == AgentRunState.Error) {
                    MessageStatus.Error
                } else {
                    MessageStatus.Complete
                }
                messages.map { message ->
                    if (message.status == MessageStatus.Streaming ||
                        message.status == MessageStatus.Pending
                    ) {
                        message.copy(status = terminal)
                    } else {
                        message
                    }
                }
            }
        }
    }

    private fun mergeHistory(
        local: List<ChatMessage>,
        incoming: List<ChatMessage>,
        runState: AgentRunState?,
    ): List<ChatMessage> {
        val localById = local.associateBy { it.id }
        val merged = incoming.map { remote ->
            val prev = localById[remote.id] ?: return@map remote
            mergeRemoteMessage(prev, remote)
        }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        val stillRunning = runState == AgentRunState.Streaming ||
            runState == AgentRunState.WaitingApproval ||
            runState == AgentRunState.WaitingAskUser
        val extras = local.filter { message ->
            message.id !in incomingIds &&
                message.role == ChatRole.Assistant &&
                (message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending) &&
                stillRunning
        }
        return (merged + extras).sortedBy { it.createdAtEpochMs }
    }

    private fun mergeRemoteMessage(local: ChatMessage, incoming: ChatMessage): ChatMessage {
        val incomingLive = incoming.status == MessageStatus.Streaming ||
            incoming.status == MessageStatus.Pending
        val content = if (incomingLive) {
            if (local.content.length > incoming.content.length) local.content else incoming.content
        } else {
            incoming.content.ifBlank { local.content }
        }
        val localReasoning = local.reasoning.orEmpty()
        val incomingReasoning = incoming.reasoning.orEmpty()
        val reasoning = if (incomingLive) {
            when {
                localReasoning.length > incomingReasoning.length -> local.reasoning
                incomingReasoning.isNotBlank() -> incoming.reasoning
                else -> local.reasoning
            }
        } else {
            incoming.reasoning?.takeIf { it.isNotBlank() } ?: local.reasoning
        }
        return incoming.copy(
            content = content,
            reasoning = reasoning,
            toolActivities = incoming.toolActivities.ifEmpty { local.toolActivities },
            codeChanges = incoming.codeChanges.ifEmpty { local.codeChanges },
            planTasks = incoming.planTasks.ifEmpty { local.planTasks },
            sharedFiles = incoming.sharedFiles.ifEmpty { local.sharedFiles },
            sharedUrls = incoming.sharedUrls.ifEmpty { local.sharedUrls },
            createdAtEpochMs = if (incoming.createdAtEpochMs > 0L) {
                incoming.createdAtEpochMs
            } else {
                local.createdAtEpochMs
            },
        )
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
        if (finished && success) {
            ingestSharedCardFromActivity(
                sessionId = sessionId,
                activity = activity,
                createdAtEpochMs = System.currentTimeMillis(),
            )
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

    private fun hydrateSharedCardsFromHistory(sessionId: String, messages: List<ChatMessage>) {
        val workspaceId = _sessions.value.find { it.id == sessionId }?.workspaceId
        val seen = localSharedOfferIds(sessionId).toMutableSet()
        for (message in messages) {
            for (activity in message.toolActivities) {
                ingestSharedCardFromActivity(
                    sessionId = sessionId,
                    activity = activity,
                    createdAtEpochMs = message.createdAtEpochMs,
                    workspaceId = workspaceId,
                    seenOfferIds = seen,
                )
            }
        }
    }

    private fun ingestSharedCardFromActivity(
        sessionId: String,
        activity: ToolActivity,
        createdAtEpochMs: Long,
        workspaceId: String? = _sessions.value.find { it.id == sessionId }?.workspaceId,
        seenOfferIds: MutableSet<String>? = null,
    ) {
        if (!activity.success) return
        val status = activity.status.lowercase()
        if (status.isNotEmpty() && status != "done" && status != "complete" && status != "completed") {
            return
        }
        when (activity.toolName) {
            "share_to_companion" -> {
                val path = extractSharePath(activity.arguments, activity.result)
                if (path.isEmpty()) return
                val offerId = extractOfferId(activity.result, activity.id)
                if (!markNewOffer(sessionId, offerId, seenOfferIds)) return
                val name = activity.arguments?.string("label")?.trim()?.ifBlank { null }
                    ?: path.replace('\\', '/').substringAfterLast('/').ifBlank { path }
                val result = activity.result.orEmpty()
                val mime = SHARE_MIME_RE.find(result)?.groupValues?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?: "application/octet-stream"
                val size = SHARE_SIZE_RE.find(result)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                upsertLocalSharedMessage(
                    ChatMessage(
                        id = "shared-$offerId",
                        sessionId = sessionId,
                        role = ChatRole.System,
                        content = "",
                        status = MessageStatus.Complete,
                        createdAtEpochMs = createdAtEpochMs.takeIf { it > 0L }
                            ?: System.currentTimeMillis(),
                        sharedFiles = listOf(
                            ChatSharedFile(
                                offerId = offerId,
                                path = path,
                                name = name,
                                mime = mime,
                                size = size,
                                status = SharedFileStatus.Offered,
                                workspaceId = workspaceId,
                            ),
                        ),
                    ),
                )
            }
            "share_preview_url" -> {
                val result = activity.result.orEmpty()
                val publicUrl = SHARE_URL_RE.find(result)?.value
                    ?.trimEnd(',', '.', ')', ']', '"')
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val offerId = extractOfferId(activity.result, activity.id)
                if (!markNewOffer(sessionId, offerId, seenOfferIds)) return
                val label = activity.arguments?.string("label")?.trim()?.ifBlank { null } ?: "Preview"
                val originUrl = activity.arguments?.string("url").orEmpty()
                upsertLocalSharedMessage(
                    ChatMessage(
                        id = "shared-url-$offerId",
                        sessionId = sessionId,
                        role = ChatRole.System,
                        content = "",
                        status = MessageStatus.Complete,
                        createdAtEpochMs = createdAtEpochMs.takeIf { it > 0L }
                            ?: System.currentTimeMillis(),
                        sharedUrls = listOf(
                            ChatSharedUrl(
                                offerId = offerId,
                                label = label,
                                publicUrl = publicUrl,
                                originUrl = originUrl,
                            ),
                        ),
                    ),
                )
            }
            else -> Unit
        }
    }

    private fun markNewOffer(
        sessionId: String,
        offerId: String,
        seenOfferIds: MutableSet<String>?,
    ): Boolean {
        if (offerId.isBlank()) return false
        if (seenOfferIds != null) {
            if (offerId in seenOfferIds) return false
            seenOfferIds += offerId
            return true
        }
        return offerId !in localSharedOfferIds(sessionId)
    }

    private fun localSharedOfferIds(sessionId: String): Set<String> =
        localSharedBySession.value[sessionId].orEmpty().flatMap { message ->
            message.sharedFiles.map { it.offerId } + message.sharedUrls.map { it.offerId }
        }.toSet()

    private fun extractSharePath(arguments: JsonObject?, result: String?): String {
        val fromArgs = arguments?.string("path")?.let(::normalizeSharedFilePath).orEmpty()
        if (fromArgs.isNotEmpty()) return fromArgs
        val match = SHARE_PATH_RE.find(result.orEmpty()) ?: return ""
        val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
        return normalizeSharedFilePath(raw)
    }

    private fun extractOfferId(result: String?, fallback: String): String {
        val fromResult = result?.let { SHARE_OFFER_ID_RE.find(it)?.groupValues?.getOrNull(1) }
        return fromResult?.ifBlank { null } ?: fallback
    }

    private fun currentDeviceId(): String =
        connectionRepository.credential.value?.deviceId.orEmpty()

    private fun resetLocalProjection() {
        _sessions.value = emptyList()
        _workspaces.value = emptyList()
        messagesBySession.value = emptyMap()
        localSharedBySession.value = emptyMap()
        composeBySession.value = emptyMap()
        tasksBySession.value = emptyMap()
        _models.value = emptyList()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        val SHARE_OFFER_ID_RE = Regex("""offerId=([A-Za-z0-9-]+)""")
        val SHARE_MIME_RE = Regex("""mime=([^,\s)]+)""")
        val SHARE_SIZE_RE = Regex("""size=(\d+)""")
        // Do not stop at spaces — Windows paths often contain them.
        val SHARE_PATH_RE = Regex(
            """path\s*=\s*(?:"([^"]+)"|'([^']+)'|(.+?))(?=\s*,\s*(?:mime|size|offerId)\s*=|\s*\)\s*$|$)""",
            RegexOption.IGNORE_CASE,
        )
        val SHARE_URL_RE = Regex("""https?://\S+""")
    }
}

@Singleton
public class DefaultApprovalRepository @Inject constructor(
    private val gateway: RemoteGatewayClient,
    private val json: Json,
    private val connectionRepository: ConnectionRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ApprovalRepository {

    private val _pending = MutableStateFlow<List<PendingApproval>>(emptyList())
    override val pending: StateFlow<List<PendingApproval>> = _pending.asStateFlow()

    init {
        appScope.launch {
            var seen = false
            var lastId: String? = null
            connectionRepository.credential
                .map { it?.deviceId }
                .distinctUntilChanged()
                .collect { id ->
                    if (seen && lastId != id) {
                        _pending.value = emptyList()
                    }
                    seen = true
                    lastId = id
                }
        }
        appScope.launch {
            gateway.incoming.collect { message ->
                when (message) {
                    is ServerMessage.HelloOk -> {
                        // Old desktops have no interaction.snapshot; drop stale cards
                        // so replay (or the snapshot that follows) is the authority.
                        _pending.value = emptyList()
                    }
                    is ServerMessage.Event -> when (message.name) {
                    "interaction.snapshot", "interaction-snapshot" -> {
                        applyInteractionSnapshot(message.data)
                    }
                    "tool.approval", "tool-approval", "AskUser", "ask.user", "ask-user",
                    "path.permission", "path-permission", "PathPermission",
                    -> {
                        val requestId = message.data.string("requestId") ?: return@collect
                        val sessionId = message.data.string("sessionId").orEmpty()
                        val title = message.data.string("title")
                            ?: message.data.string("toolName")
                            ?: "需要审批"
                        val kind = when (message.name) {
                            "AskUser", "ask.user", "ask-user" -> ApprovalKind.AskUser
                            "path.permission", "path-permission", "PathPermission" ->
                                ApprovalKind.PathPermission
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

    private fun applyInteractionSnapshot(data: JsonObject) {
        val items = data["pending"]?.let { element ->
            runCatching { json.decodeFromJsonElement<List<JsonObject>>(element) }.getOrNull()
        }.orEmpty()
        val now = System.currentTimeMillis()
        _pending.value = items.mapNotNull { obj ->
            val requestId = obj.string("requestId") ?: return@mapNotNull null
            val kind = when (obj.string("kind").orEmpty().lowercase()) {
                "ask_user", "ask-user", "askuser" -> ApprovalKind.AskUser
                "path_permission", "path-permission", "pathpermission" -> ApprovalKind.PathPermission
                else -> ApprovalKind.Tool
            }
            val questions = obj["questions"]?.let { element ->
                runCatching { json.decodeFromJsonElement<List<AskUserQuestion>>(element) }
                    .getOrElse { emptyList() }
            }.orEmpty()
            PendingApproval(
                requestId = requestId,
                sessionId = obj.string("sessionId").orEmpty(),
                kind = kind,
                title = obj.string("title")
                    ?: obj.string("toolName")
                    ?: "需要审批",
                toolName = obj.string("toolName"),
                previewSummary = obj.string("preview"),
                questions = questions,
                createdAtEpochMs = now,
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

@Singleton
public class DefaultWorkspaceRepository @Inject constructor(
    private val gateway: RemoteGatewayClient,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val attachCatalogStore: AttachCatalogStore,
    private val connectionRepository: ConnectionRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) : WorkspaceRepository {

    private val _snapshot = MutableStateFlow<WorkspaceSnapshot?>(null)
    override val snapshot: StateFlow<WorkspaceSnapshot?> = _snapshot.asStateFlow()

    private val _filesCatalog = MutableStateFlow<WorkspaceFilesCatalog?>(null)
    override val filesCatalog: StateFlow<WorkspaceFilesCatalog?> = _filesCatalog.asStateFlow()

    private val _skills = MutableStateFlow<List<SkillSummary>>(emptyList())
    override val skills: StateFlow<List<SkillSummary>> = _skills.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<McpServerSummary>>(emptyList())
    override val mcpServers: StateFlow<List<McpServerSummary>> = _mcpServers.asStateFlow()

    init {
        appScope.launch {
            var seen = false
            var lastId: String? = null
            connectionRepository.credential
                .map { it?.deviceId }
                .distinctUntilChanged()
                .collect { id ->
                    if (seen && lastId != id) {
                        _snapshot.value = null
                        _filesCatalog.value = null
                        _skills.value = emptyList()
                        _mcpServers.value = emptyList()
                    }
                    seen = true
                    lastId = id
                }
        }
    }

    private companion object {
        const val MAX_UPLOAD_BYTES: Long = 500L * 1024L * 1024L
        const val UPLOAD_CHUNK_BYTES: Int = 512 * 1024
        const val DOWNLOAD_CHUNK_BYTES: Int = 512 * 1024
        const val DOWNLOAD_CHUNK_TIMEOUT_MS: Long = 60_000
        /** Concurrent in-flight upload chunks (pipelined over the socket). */
        const val UPLOAD_CONCURRENCY: Int = 4
        /** Parallel HTTP Range segments for downloads. */
        const val DOWNLOAD_SEGMENTS: Int = 4
        const val DOWNLOAD_PARALLEL_MIN_BYTES: Long = 4L * 1024L * 1024L
    }

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
                    val payload = rpc.data as? JsonObject
                    AnyaResult.Success(
                        FileContent(
                            path = payload?.string("path") ?: path,
                            content = payload?.string("content").orEmpty(),
                            truncated = payload?.get("truncated")?.jsonPrimitive?.booleanOrNull ?: false,
                            size = payload?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun downloadFile(
        path: String,
        sessionId: String?,
        workspaceId: String?,
        onProgress: ((Long, Long) -> Unit)?,
    ): AnyaResult<DownloadedWorkspaceFile> = withContext(Dispatchers.IO) {
        if (_snapshot.value?.rootPath.isNullOrBlank() && _filesCatalog.value?.rootPath.isNullOrBlank()) {
            runCatching { refresh(sessionId) }
        }
        val root = _snapshot.value?.rootPath ?: _filesCatalog.value?.rootPath
        val candidates = downloadPathCandidates(path, root)
        if (candidates.isEmpty()) {
            return@withContext AnyaResult.Failure(AnyaError.Unknown("empty download path"))
        }
        var lastError: Throwable? = null
        for (candidate in candidates) {
            val http = runCatching {
                downloadViaHttp(candidate, sessionId, workspaceId, onProgress)
            }
            http.getOrNull()?.let { return@withContext AnyaResult.Success(it) }
            lastError = http.exceptionOrNull()
            Timber.w(lastError, "HTTP download failed for %s", candidate)
            if (lastError?.message?.let(::isRetryableDownloadError) == false) break
        }
        for (candidate in candidates) {
            val streamed = runCatching {
                downloadViaReadFile(candidate, sessionId, workspaceId, onProgress)
            }
            streamed.getOrNull()?.let { return@withContext AnyaResult.Success(it) }
            lastError = streamed.exceptionOrNull()
            Timber.w(lastError, "workspace.readFile download failed for %s", candidate)
            if (lastError?.message?.let(::isRetryableDownloadError) == false) break
        }
        AnyaResult.Failure(AnyaError.Unknown(lastError?.message ?: "缓存文件失败"))
    }

    private suspend fun downloadViaHttp(
        path: String,
        sessionId: String?,
        workspaceId: String?,
        onProgress: ((Long, Long) -> Unit)?,
    ): DownloadedWorkspaceFile {
        var dest: File? = null
        try {
            val beginId = UUID.randomUUID().toString()
            val begun = gateway.request(
                ClientMessage.FileDownloadBegin(
                    requestId = beginId,
                    path = path,
                    sessionId = sessionId,
                    workspaceId = workspaceId,
                ),
                beginId,
                timeoutMs = 30_000,
            )
            val beginRpc = when (begun) {
                is AnyaResult.Failure -> error(begun.error.toString())
                is AnyaResult.Success -> begun.data
            }
            if (!beginRpc.ok) {
                error(beginRpc.error ?: "file.download.begin failed")
            }
            val payload = beginRpc.data as? JsonObject
                ?: error("file.download.begin returned no payload")
            val rawUrl = payload.string("url")
                ?: error("file.download.begin returned no url")
            val connected = gateway.connectedCredential()
                ?: connectionRepository.credential.value?.transportEndpoint()
                ?: error("gateway is not connected")
            val url = connected.rewriteHttpUrl(rawUrl)
            if (url != rawUrl) {
                Timber.i("Rewrote download URL %s → %s", rawUrl, url)
            }
            val size = payload["size"]?.jsonPrimitive?.longOrNull ?: 0L
            if (size > MAX_UPLOAD_BYTES) {
                error("file too large: $size bytes (max $MAX_UPLOAD_BYTES)")
            }
            val name = payload.string("name")
                ?: path.replace('\\', '/').substringAfterLast('/').ifBlank { "file.bin" }
            val mime = payload.string("mime") ?: "application/octet-stream"
            val created = uniqueSharedCacheFile(name)
            dest = created

            val downloadClient = okHttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val segments = if (size >= DOWNLOAD_PARALLEL_MIN_BYTES) DOWNLOAD_SEGMENTS else 1
            val downloadedBytes = AtomicLong(0L)
            RandomAccessFile(created, "rw").use { raf ->
                raf.setLength(size)
                val channel = raf.channel
                coroutineScope {
                    (0 until segments).map { idx ->
                        val start = idx * size / segments
                        val end = (idx + 1) * size / segments - 1
                        async {
                            var pos = start
                            var attempts = 0
                            while (pos <= end) {
                                try {
                                    val request = Request.Builder()
                                        .url(url)
                                        .header("Range", "bytes=$pos-$end")
                                        .build()
                                    downloadClient.newCall(request).execute().use { resp ->
                                        val rangeOk = resp.code == 206
                                        val fullOk = segments == 1 && resp.code == 200
                                        if (!rangeOk && !fullOk) {
                                            error("download failed: HTTP ${resp.code}")
                                        }
                                        val body = resp.body ?: error("download returned no body")
                                        body.byteStream().use { input ->
                                            val buf = ByteArray(128 * 1024)
                                            while (true) {
                                                val n = input.read(buf)
                                                if (n <= 0) break
                                                channel.write(ByteBuffer.wrap(buf, 0, n), pos)
                                                pos += n
                                                val d = downloadedBytes.addAndGet(n.toLong())
                                                onProgress?.invoke(d, size)
                                            }
                                        }
                                    }
                                    break
                                } catch (t: Throwable) {
                                    attempts++
                                    if (attempts >= 3) throw t
                                }
                            }
                            if (pos <= end) error("incomplete download segment")
                        }
                    }.awaitAll()
                }
            }
            if (created.length() != size) {
                error("incomplete download: ${created.length()} of $size bytes")
            }
            return DownloadedWorkspaceFile(
                path = path,
                name = name,
                mime = mime,
                size = created.length(),
                localPath = created.absolutePath,
                localUri = fileProviderUri(created).toString(),
            )
        } catch (t: Throwable) {
            dest?.delete()
            throw t
        }
    }

    private suspend fun downloadViaReadFile(
        path: String,
        sessionId: String?,
        workspaceId: String?,
        onProgress: ((Long, Long) -> Unit)?,
    ): DownloadedWorkspaceFile {
        var dest: File? = null
        try {
            var name = path.replace('\\', '/').substringAfterLast('/').ifBlank { "file.bin" }
            var mime = "application/octet-stream"
            val created = uniqueSharedCacheFile(name)
            dest = created
            var offset = 0L
            var total = 0L
            FileOutputStream(created).use { out ->
                while (true) {
                    val requestId = UUID.randomUUID().toString()
                    val rpc = gateway.request(
                        ClientMessage.WorkspaceReadFile(
                            requestId = requestId,
                            path = path,
                            sessionId = sessionId,
                            workspaceId = workspaceId,
                            mode = "download",
                            offset = offset,
                            length = DOWNLOAD_CHUNK_BYTES.toLong(),
                        ),
                        requestId,
                        timeoutMs = DOWNLOAD_CHUNK_TIMEOUT_MS,
                    )
                    val result = when (rpc) {
                        is AnyaResult.Failure -> error(rpc.error.toString())
                        is AnyaResult.Success -> rpc.data
                    }
                    if (!result.ok) {
                        error(result.error ?: "workspace.readFile download failed")
                    }
                    val payload = result.data as? JsonObject
                        ?: error("workspace.readFile returned no payload")
                    payload.string("name")?.takeIf { it.isNotBlank() }?.let { name = it }
                    payload.string("mime")?.takeIf { it.isNotBlank() }?.let { mime = it }
                    val reported = payload["size"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (reported > 0L) total = reported
                    if (total > MAX_UPLOAD_BYTES) {
                        error("file too large: $total bytes (max $MAX_UPLOAD_BYTES)")
                    }
                    val chunkB64 = payload.string("dataBase64")
                        ?: payload.string("content").orEmpty()
                    val eof = payload["eof"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (chunkB64.isNotEmpty()) {
                        val bytes = decodeBase64(chunkB64)
                        out.write(bytes)
                        offset += bytes.size
                        onProgress?.invoke(offset, total.takeIf { it > 0L } ?: offset)
                    }
                    if (eof) break
                    if (chunkB64.isEmpty()) {
                        error("workspace.readFile returned an empty slice")
                    }
                }
            }
            val renamed = renameCachedDownload(created, name)
            dest = renamed
            return DownloadedWorkspaceFile(
                path = path,
                name = name,
                mime = mime,
                size = renamed.length(),
                localPath = renamed.absolutePath,
                localUri = fileProviderUri(renamed).toString(),
            )
        } catch (t: Throwable) {
            dest?.delete()
            throw t
        }
    }

    private fun renameCachedDownload(file: File, name: String): File {
        val target = uniqueSharedCacheFile(name)
        if (target.absolutePath == file.absolutePath) return file
        return if (file.renameTo(target)) {
            target
        } else {
            file.copyTo(target, overwrite = true)
            file.delete()
            target
        }
    }

    private fun decodeBase64(value: String): ByteArray {
        val padded = value.replace("\\s".toRegex(), "")
        return runCatching { Base64.decode(padded, Base64.DEFAULT) }
            .recoverCatching { Base64.decode(padded, Base64.URL_SAFE) }
            .getOrElse { error("invalid base64 slice") }
    }

    private fun isRetryableDownloadError(message: String): Boolean {
        val m = message.lowercase()
        return "file not found" in m ||
            "no such file" in m ||
            "not found" in m ||
            "unknown type" in m ||
            "unknown method" in m ||
            "unsupported" in m ||
            "unrecognized" in m
    }

    override suspend fun exportCachedFileToDownloads(
        localPath: String,
        name: String,
        mime: String,
    ): AnyaResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(localPath)
            if (!source.isFile) error("缓存文件不存在")
            val uri = saveToDownloads(name, mime, source)
            uri.toString()
        }.fold(
            onSuccess = { AnyaResult.Success(it) },
            onFailure = { e ->
                Timber.w(e, "Failed to export cached file")
                AnyaResult.Failure(AnyaError.Unknown(e.message ?: "导出到下载失败"))
            },
        )
    }

    private fun uniqueSharedCacheFile(name: String): File {
        val dir = File(context.filesDir, "shared").apply { mkdirs() }
        val safeName = name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "shared.bin" }
        var target = File(dir, safeName)
        if (target.exists()) {
            val base = safeName.substringBeforeLast('.', safeName)
            val ext = safeName.substringAfterLast('.', "")
            var index = 1
            while (target.exists()) {
                val candidate = if (ext.isEmpty()) "$base-$index" else "$base-$index.$ext"
                target = File(dir, candidate)
                index++
            }
        }
        return target
    }

    private fun fileProviderUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    private fun saveToDownloads(name: String, mime: String, source: File): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: error("无法写入系统下载目录")
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入系统下载目录")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        var target = File(dir, name)
        // Avoid clobbering an existing download with the same name.
        if (target.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            var index = 1
            while (target.exists()) {
                val candidate = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
                target = File(dir, candidate)
                index++
            }
        }
        source.inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(target)
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

    override suspend fun uploadLocalFile(
        sessionId: String?,
        workspaceId: String?,
        fileName: String,
        size: Long,
        mime: String?,
        input: java.io.InputStream,
        onProgress: (written: Long, total: Long) -> Unit,
    ): AnyaResult<UploadedCompanionFile> = withContext(Dispatchers.IO) {
        val maxBytes = MAX_UPLOAD_BYTES
        if (size > maxBytes) {
            return@withContext AnyaResult.Failure(
                AnyaError.Protocol("file too large: $size bytes (max $maxBytes)"),
            )
        }
        val beginId = UUID.randomUUID().toString()
        val begun = gateway.request(
            ClientMessage.FileUploadBegin(
                requestId = beginId,
                sessionId = sessionId,
                workspaceId = workspaceId,
                fileName = fileName,
                size = size,
                mime = mime,
            ),
            beginId,
            timeoutMs = 30_000,
        )
        val beginRpc = when (begun) {
            is AnyaResult.Failure -> return@withContext begun
            is AnyaResult.Success -> begun.data
        }
        if (!beginRpc.ok) {
            return@withContext AnyaResult.Failure(
                AnyaError.Network(beginRpc.error ?: "file.upload.begin failed"),
            )
        }
        val beginPayload = beginRpc.data as? JsonObject
            ?: return@withContext AnyaResult.Failure(
                AnyaError.Protocol("file.upload.begin returned no payload"),
            )
        val uploadId = beginPayload.string("uploadId")
            ?: return@withContext AnyaResult.Failure(
                AnyaError.Protocol("file.upload.begin returned no uploadId"),
            )
        val assignedSession = beginPayload.string("sessionId").orEmpty()
        suspend fun abortQuietly() {
            val abortId = UUID.randomUUID().toString()
            gateway.request(
                ClientMessage.FileUploadAbort(requestId = abortId, uploadId = uploadId),
                abortId,
                timeoutMs = 10_000,
            )
        }
        try {
            val semaphore = Semaphore(UPLOAD_CONCURRENCY)
            val writtenBytes = AtomicLong(0L)
            val buf = ByteArray(UPLOAD_CHUNK_BYTES)
            input.use { stream ->
                coroutineScope {
                    val jobs = mutableListOf<Deferred<Unit>>()
                    var offset = 0L
                    while (offset < size) {
                        val n = stream.read(buf)
                        if (n <= 0) break
                        val data = buf.copyOf(n)
                        val chunkOffset = offset
                        offset += n
                        semaphore.acquire()
                        jobs += async {
                            try {
                                // Raw binary frame — no base64 (saves ~25% bytes).
                                val chunkId = UUID.randomUUID().toString()
                                val chunked = gateway.sendBinaryChunk(
                                    uploadId = uploadId,
                                    offset = chunkOffset,
                                    data = data,
                                    requestId = chunkId,
                                    timeoutMs = 60_000,
                                )
                                val chunkRpc = when (chunked) {
                                    is AnyaResult.Failure -> error(chunked.error.toString())
                                    is AnyaResult.Success -> chunked.data
                                }
                                if (!chunkRpc.ok) {
                                    error(chunkRpc.error ?: "file.upload.chunk failed")
                                }
                                val written = writtenBytes.addAndGet(n.toLong())
                                onProgress(written, size)
                            } finally {
                                semaphore.release()
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }
            val finishId = UUID.randomUUID().toString()
            val finished = gateway.request(
                ClientMessage.FileUploadFinish(requestId = finishId, uploadId = uploadId),
                finishId,
                timeoutMs = 60_000,
            )
            val finishRpc = when (finished) {
                is AnyaResult.Failure -> {
                    abortQuietly()
                    return@withContext finished
                }
                is AnyaResult.Success -> finished.data
            }
            if (!finishRpc.ok) {
                abortQuietly()
                return@withContext AnyaResult.Failure(
                    AnyaError.Network(finishRpc.error ?: "file.upload.finish failed"),
                )
            }
            val finishPayload = finishRpc.data as? JsonObject
                ?: return@withContext AnyaResult.Failure(
                    AnyaError.Protocol("file.upload.finish returned no payload"),
                )
            val path = finishPayload.string("path")
                ?: beginPayload.string("relPath")
                ?: return@withContext AnyaResult.Failure(
                    AnyaError.Protocol("file.upload.finish returned no path"),
                )
            AnyaResult.Success(
                UploadedCompanionFile(
                    sessionId = finishPayload.string("sessionId") ?: assignedSession,
                    path = path,
                    name = finishPayload.string("name") ?: fileName,
                    size = finishPayload["size"]?.jsonPrimitive?.longOrNull ?: size,
                ),
            )
        } catch (t: Throwable) {
            abortQuietly()
            AnyaResult.Failure(AnyaError.Unknown(t.message ?: "upload failed", t))
        }
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

