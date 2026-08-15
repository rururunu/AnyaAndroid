package ai.anya.companion.core.network.gateway

import ai.anya.companion.core.common.result.AnyaError
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.model.protocol.ClientMessage
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.core.model.protocol.ServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

public enum class GatewaySocketState {
    Idle,
    Connecting,
    Open,
    Closing,
    Closed,
    Failed,
}

/**
 * Low-level WebSocket transport to Anya Desktop Remote Gateway.
 * Domain/data layers own reconnect / auth policy; this class only speaks the wire protocol.
 */
@Singleton
public class RemoteGatewayClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    public companion object {
        /**
         * Max time to wait for the public/WebSocket handshake before force-cancel.
         * Kept slightly above OkHttp's connect timeout so OkHttp reports first.
         */
        public const val CONNECT_TIMEOUT_MS: Long = 20_000L
        /** Unreachable LAN should fail fast so Cloudflare is tried within seconds. */
        public const val LAN_CONNECT_TIMEOUT_MS: Long = 3_000L
    }

    private val socketRef = AtomicReference<WebSocket?>(null)
    /** Transport endpoint of the in-flight / last [connect] attempt. */
    private val connectedRef = AtomicReference<DeviceCredential?>(null)
    /** Bumped on every connect/disconnect so superseded OkHttp callbacks cannot mutate state. */
    private val connectGeneration = AtomicInteger(0)
    private val rpcWaiters =
        ConcurrentHashMap<String, CompletableDeferred<ServerMessage.RpcResult>>()
    private val connectWatchdog = AtomicReference<ScheduledFuture<*>?>(null)
    private val connectScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "anya-gateway-connect-timeout").apply { isDaemon = true }
    }

    private val _state = MutableStateFlow(GatewaySocketState.Idle)
    public val state: StateFlow<GatewaySocketState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ServerMessage>(
        replay = 32,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    public val incoming: SharedFlow<ServerMessage> = _incoming.asSharedFlow()

    /** Host/port/scheme of the socket this client last tried to open. */
    public fun connectedCredential(): DeviceCredential? = connectedRef.get()

    public fun connect(
        credential: DeviceCredential,
        timeoutMs: Long = CONNECT_TIMEOUT_MS,
    ): AnyaResult<Unit> {
        val generation = connectGeneration.incrementAndGet()
        connectedRef.set(credential)
        // Quietly drop any prior socket so callers can reconnect without a Closed → reconnect race.
        failPending("Gateway reconnecting")
        cancelConnectWatchdog()
        socketRef.getAndSet(null)?.cancel()
        okHttpClient.connectionPool.evictAll()
        _state.value = GatewaySocketState.Connecting

        val portSuffix = when {
            credential.scheme == "wss" && credential.port == 443 -> ""
            credential.scheme == "ws" && credential.port == 80 -> ""
            else -> ":${credential.port}"
        }
        val url = "${credential.scheme}://${credential.host}$portSuffix/remote/v1"
        val request = Request.Builder()
            .url(url)
            .header("X-Anya-Device-Id", credential.deviceId)
            .build()

        val hello = json.encodeToString(
            ClientMessage.serializer(),
            ClientMessage.Hello(
                deviceId = credential.deviceId,
                credential = credential.credential,
                appVersion = "0.1.2",
            ),
        )
        return try {
            val socket = okHttpClient.newWebSocket(request, Listener(generation, hello))
            if (connectGeneration.get() != generation) {
                socket.cancel()
                return AnyaResult.Success(Unit)
            }
            socketRef.set(socket)
            armConnectWatchdog(socket, generation, timeoutMs)
            AnyaResult.Success(Unit)
        } catch (t: Throwable) {
            cancelConnectWatchdog()
            if (connectGeneration.get() == generation) {
                _state.value = GatewaySocketState.Failed
            }
            AnyaResult.Failure(AnyaError.Network("Failed to open gateway socket", t))
        }
    }

    public fun send(message: ClientMessage): AnyaResult<Unit> {
        val socket = socketRef.get()
            ?: return AnyaResult.Failure(AnyaError.Network("Gateway is not connected"))
        return try {
            val payload = json.encodeToString(ClientMessage.serializer(), message)
            val ok = socket.send(payload)
            if (ok) AnyaResult.Success(Unit)
            else AnyaResult.Failure(AnyaError.Network("WebSocket send failed"))
        } catch (t: Throwable) {
            AnyaResult.Failure(AnyaError.Protocol(t.message ?: "Encode failed"))
        }
    }

    /**
     * Send an RPC and wait for the matching [ServerMessage.RpcResult].
     * Prefer this when the caller needs the payload before continuing.
     */
    public suspend fun request(
        message: ClientMessage,
        requestId: String,
        timeoutMs: Long = 15_000,
    ): AnyaResult<ServerMessage.RpcResult> {
        val deferred = CompletableDeferred<ServerMessage.RpcResult>()
        rpcWaiters[requestId] = deferred
        when (val sent = send(message)) {
            is AnyaResult.Failure -> {
                rpcWaiters.remove(requestId, deferred)
                return sent
            }
            is AnyaResult.Success -> Unit
        }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        rpcWaiters.remove(requestId, deferred)
        return if (result != null) {
            AnyaResult.Success(result)
        } else {
            AnyaResult.Failure(AnyaError.Network("RPC timed out: $requestId"))
        }
    }

    public fun sendBinary(payload: ByteArray): AnyaResult<Unit> {
        val socket = socketRef.get()
            ?: return AnyaResult.Failure(AnyaError.Network("Gateway is not connected"))
        return try {
            val ok = socket.send(okio.ByteString.of(*payload))
            if (ok) AnyaResult.Success(Unit)
            else AnyaResult.Failure(AnyaError.Network("WebSocket send failed"))
        } catch (t: Throwable) {
            AnyaResult.Failure(AnyaError.Protocol(t.message ?: "Encode failed"))
        }
    }

    /**
     * Send one raw upload chunk as a binary frame and wait for its RPC ack.
     * Frame layout: `[requestId:36][uploadId:36][offset:8 big-endian][data]`.
     */
    public suspend fun sendBinaryChunk(
        uploadId: String,
        offset: Long,
        data: ByteArray,
        requestId: String,
        timeoutMs: Long = 60_000,
    ): AnyaResult<ServerMessage.RpcResult> {
        val deferred = CompletableDeferred<ServerMessage.RpcResult>()
        rpcWaiters[requestId] = deferred

        val frame = ByteArray(80 + data.size)
        requestId.toByteArray(Charsets.US_ASCII).copyInto(frame, 0)
        uploadId.toByteArray(Charsets.US_ASCII).copyInto(frame, 36)
        var o = offset
        for (i in 7 downTo 0) {
            frame[72 + i] = (o and 0xFF).toByte()
            o = o shr 8
        }
        data.copyInto(frame, 80)

        when (val sent = sendBinary(frame)) {
            is AnyaResult.Failure -> {
                rpcWaiters.remove(requestId, deferred)
                return sent
            }
            is AnyaResult.Success -> Unit
        }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        rpcWaiters.remove(requestId, deferred)
        return if (result != null) {
            AnyaResult.Success(result)
        } else {
            AnyaResult.Failure(AnyaError.Network("RPC timed out: $requestId"))
        }
    }

    public fun disconnect() {
        connectGeneration.incrementAndGet()
        cancelConnectWatchdog()
        failPending("Gateway disconnected")
        connectedRef.set(null)
        socketRef.getAndSet(null)?.close(1000, "client disconnect")
        _state.value = GatewaySocketState.Closed
    }

    private fun armConnectWatchdog(socket: WebSocket, generation: Int, timeoutMs: Long) {
        val future = connectScheduler.schedule(
            {
                if (connectGeneration.get() != generation) return@schedule
                if (_state.value == GatewaySocketState.Connecting && socketRef.get() === socket) {
                    Timber.w("Gateway connect timed out after %dms", timeoutMs)
                    failPending("Gateway connect timed out")
                    socketRef.compareAndSet(socket, null)
                    socket.cancel()
                    _state.value = GatewaySocketState.Failed
                }
            },
            timeoutMs,
            TimeUnit.MILLISECONDS,
        )
        connectWatchdog.getAndSet(future)?.cancel(false)
    }

    private fun cancelConnectWatchdog() {
        connectWatchdog.getAndSet(null)?.cancel(false)
    }

    private fun failPending(reason: String) {
        val error = AnyaError.Network(reason)
        rpcWaiters.keys.toList().forEach { id ->
            rpcWaiters.remove(id)?.complete(
                ServerMessage.RpcResult(
                    requestId = id,
                    ok = false,
                    error = error.message,
                ),
            )
        }
    }

    private fun completeRpc(result: ServerMessage.RpcResult) {
        rpcWaiters.remove(result.requestId)?.complete(result)
    }

    private inner class Listener(
        private val generation: Int,
        private val helloPayload: String,
    ) : WebSocketListener() {
        private fun isCurrent(): Boolean = connectGeneration.get() == generation

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent()) return
            cancelConnectWatchdog()
            // Send hello before returning so the first WS frame is on the wire
            // before OkHttp starts loopReader. Cloudflare / DPI often RST a
            // 101 that sits idle for even a few hundred milliseconds.
            val helloQueued = webSocket.send(helloPayload)
            _state.value = GatewaySocketState.Open
            Timber.i(
                "Gateway connected: %s (hello queued=%s)",
                response.request.url,
                helloQueued,
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent()) return
            try {
                val message = json.decodeFromString(ServerMessage.serializer(), text)
                if (message is ServerMessage.RpcResult) {
                    completeRpc(message)
                }
                _incoming.tryEmit(message)
            } catch (t: Throwable) {
                Timber.w(t, "Dropping undecodable gateway frame")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent()) return
            cancelConnectWatchdog()
            _state.value = GatewaySocketState.Closing
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent()) return
            cancelConnectWatchdog()
            _state.value = GatewaySocketState.Closed
            socketRef.compareAndSet(webSocket, null)
            failPending("Gateway closed")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent()) {
                if (isCanceled(t)) {
                    Timber.d("Superseded gateway attempt canceled")
                } else {
                    Timber.d(t, "Ignoring stale gateway failure")
                }
                return
            }
            cancelConnectWatchdog()
            when {
                isCanceled(t) -> Timber.i(t, "Gateway canceled")
                isTransientTlsDrop(t) -> Timber.w(t, "Gateway TLS handshake dropped")
                else -> Timber.e(t, "Gateway failure")
            }
            _state.value = GatewaySocketState.Failed
            socketRef.compareAndSet(webSocket, null)
            failPending(t.message ?: "Gateway failure")
        }
    }
}

private fun isCanceled(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        if (cur is IOException && cur.message == "Canceled") return true
        if (cur is java.net.SocketException) {
            val msg = cur.message.orEmpty()
            if (msg.contains("Socket closed", ignoreCase = true) ||
                msg.contains("canceled", ignoreCase = true)
            ) {
                return true
            }
        }
        cur = cur.cause
    }
    return false
}

/** Peer/middlebox closed the TCP stream during TLS — common on Cloudflare in China. */
private fun isTransientTlsDrop(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        if (cur is javax.net.ssl.SSLHandshakeException) return true
        if (cur is javax.net.ssl.SSLException) return true
        cur = cur.cause
    }
    return false
}
