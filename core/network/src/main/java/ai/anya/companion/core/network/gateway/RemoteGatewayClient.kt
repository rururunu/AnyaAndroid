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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
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
        /** Max time to wait for the WebSocket to become Open before force-cancel. */
        public const val CONNECT_TIMEOUT_MS: Long = 60_000L
    }

    private val socketRef = AtomicReference<WebSocket?>(null)
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

    public fun connect(credential: DeviceCredential): AnyaResult<Unit> {
        // Quietly drop any prior socket so callers can reconnect without a Closed → reconnect race.
        failPending("Gateway reconnecting")
        cancelConnectWatchdog()
        socketRef.getAndSet(null)?.cancel()
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

        return try {
            val socket = okHttpClient.newWebSocket(request, Listener())
            socketRef.set(socket)
            armConnectWatchdog(socket)
            AnyaResult.Success(Unit)
        } catch (t: Throwable) {
            cancelConnectWatchdog()
            _state.value = GatewaySocketState.Failed
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

    public fun disconnect() {
        cancelConnectWatchdog()
        failPending("Gateway disconnected")
        socketRef.getAndSet(null)?.close(1000, "client disconnect")
        _state.value = GatewaySocketState.Closed
    }

    private fun armConnectWatchdog(socket: WebSocket) {
        val future = connectScheduler.schedule(
            {
                if (_state.value == GatewaySocketState.Connecting && socketRef.get() === socket) {
                    Timber.w("Gateway connect timed out after %dms", CONNECT_TIMEOUT_MS)
                    failPending("Gateway connect timed out")
                    socketRef.compareAndSet(socket, null)
                    socket.cancel()
                    _state.value = GatewaySocketState.Failed
                }
            },
            CONNECT_TIMEOUT_MS,
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

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            cancelConnectWatchdog()
            _state.value = GatewaySocketState.Open
            Timber.i("Gateway connected: %s", response.request.url)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
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
            cancelConnectWatchdog()
            _state.value = GatewaySocketState.Closing
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            cancelConnectWatchdog()
            _state.value = GatewaySocketState.Closed
            socketRef.compareAndSet(webSocket, null)
            failPending("Gateway closed")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            cancelConnectWatchdog()
            Timber.e(t, "Gateway failure")
            _state.value = GatewaySocketState.Failed
            socketRef.compareAndSet(webSocket, null)
            failPending(t.message ?: "Gateway failure")
        }
    }
}
