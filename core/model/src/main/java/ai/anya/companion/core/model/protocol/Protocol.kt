package ai.anya.companion.core.model.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Wire protocol between Anya Desktop Remote Gateway and this companion.
 * Keep camelCase to match Anya BusEvent / IPC DTOs.
 */
@Serializable
public data class PairingPayload(
    public val host: String,
    public val port: Int,
    public val pairingToken: String,
    public val deviceName: String? = null,
    public val scheme: String = "wss",
    /** Optional same-LAN endpoint advertised alongside a public tunnel host. */
    public val lanHost: String? = null,
    public val lanPort: Int? = null,
    /** User-chosen label shown in the top bar and device list. */
    public val displayName: String? = null,
)

@Serializable
public data class DeviceCredential(
    public val deviceId: String,
    public val credential: String,
    public val host: String,
    public val port: Int,
    public val scheme: String = "wss",
    public val pairedAtEpochMs: Long,
    /** Prefer this LAN endpoint when reachable (same Wi-Fi as desktop). */
    public val lanHost: String? = null,
    public val lanPort: Int? = null,
    /** Last endpoint that completed hello.ok — tried first on reconnect. */
    public val lastGoodHost: String? = null,
    public val lastGoodPort: Int? = null,
    public val lastGoodScheme: String? = null,
    /** User-chosen host label. Empty falls back to [HostDisplayName.DEFAULT]. */
    public val displayName: String = "",
) {
    public fun resolvedDisplayName(): String =
        HostDisplayName.orFallback(displayName)

    public fun endpointKey(): String = "$scheme://$host:$port"

    public fun lastGoodEndpointKey(): String? {
        val host = lastGoodHost?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val scheme = lastGoodScheme?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = lastGoodPort?.takeIf { it in 1..65535 } ?: return null
        return "$scheme://$host:$port"
    }

    /** WebSocket origin matching [scheme]/[host]/[port]. */
    public fun wsOrigin(): String {
        val ws = when (scheme.lowercase()) {
            "https", "wss" -> "wss"
            else -> "ws"
        }
        return origin(ws, host, port)
    }

    /** HTTP origin matching [scheme]/[host]/[port] (`ws`→`http`, `wss`→`https`). */
    public fun httpOrigin(): String {
        val http = when (scheme.lowercase()) {
            "https", "wss" -> "https"
            else -> "http"
        }
        return origin(http, host, port)
    }

    /**
     * Endpoint that completed `hello.ok`, else the primary pairing host.
     * Used when rewriting desktop-minted HTTP URLs onto the live transport.
     */
    public fun transportEndpoint(): DeviceCredential {
        val liveHost = lastGoodHost?.trim()?.takeIf { it.isNotEmpty() } ?: return this
        if (isLoopbackLanHost(liveHost)) return this
        return copy(
            host = liveHost,
            port = lastGoodPort?.takeIf { it in 1..65535 } ?: port,
            scheme = lastGoodScheme?.trim()?.takeIf { it.isNotEmpty() } ?: scheme,
        )
    }

    /**
     * Desktop often mints download/preview URLs against loopback (the PC).
     * Keep path/query/fragment and point the origin at this phone's live gateway.
     */
    public fun rewriteHttpUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return url
        val base = httpOrigin()
        val parsed = runCatching { java.net.URI(trimmed) }.getOrNull()
        val pathQuery = if (parsed == null ||
            parsed.scheme.isNullOrBlank() ||
            parsed.host.isNullOrBlank()
        ) {
            if (trimmed.contains("://")) return trimmed
            if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        } else {
            val path = parsed.rawPath.orEmpty().ifBlank { "/" }
            val query = parsed.rawQuery?.let { "?$it" }.orEmpty()
            val fragment = parsed.rawFragment?.let { "#$it" }.orEmpty()
            path + query + fragment
        }
        return base + pathQuery
    }

    /** Ordered connect candidates: LAN first (ws), then the primary public/LAN host. */
    public fun connectCandidates(): List<DeviceCredential> {
        val primary = takeUnless { isLoopbackLanHost(host) }
        val lan = lanHost?.trim()?.takeIf { it.isNotEmpty() }?.let { host ->
            // Loopback is the desktop itself — on the phone it is never the gateway.
            if (isLoopbackLanHost(host)) return@let null
            val port = lanPort?.takeIf { it in 1..65535 } ?: this.port
            // Skip a duplicate of the primary endpoint.
            if (primary != null && host == primary.host && port == primary.port && primary.scheme == "ws") {
                null
            } else {
                copy(host = host, port = port, scheme = "ws")
            }
        }
        return listOfNotNull(lan, primary)
    }
}

private fun origin(scheme: String, host: String, port: Int): String {
    val hostPart = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    val omitPort = (scheme == "https" && port == 443) ||
        (scheme == "wss" && port == 443) ||
        (scheme == "http" && port == 80) ||
        (scheme == "ws" && port == 80)
    return if (omitPort) "$scheme://$hostPart" else "$scheme://$hostPart:$port"
}

public fun isLoopbackLanHost(host: String): Boolean {
    val h = host.trim().trimStart('[').trimEnd(']').trimEnd('.').lowercase()
    return h == "localhost" ||
        h == "::1" ||
        h == "0.0.0.0" ||
        h.startsWith("127.") ||
        h == "::ffff:127.0.0.1" ||
        h.endsWith("127.0.0.1")
}

@Serializable
public sealed class ClientMessage {
    @Serializable
    @SerialName("hello")
    public data class Hello(
        public val protocolVersion: Int = PROTOCOL_VERSION,
        public val deviceId: String,
        public val credential: String,
        public val appVersion: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("chat.send")
    public data class ChatSend(
        public val requestId: String,
        public val sessionId: String?,
        public val message: String,
        public val workspaceId: String? = null,
        public val chatMode: String? = null,
        public val toolApprovalMode: String? = null,
        public val chatModel: String? = null,
        public val chatModelProvider: String? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("chat.cancel")
    public data class ChatCancel(
        public val requestId: String,
        public val messageId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("session.list")
    public data class SessionList(
        public val requestId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("session.history")
    public data class SessionHistory(
        public val requestId: String,
        public val sessionId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("session.delete")
    public data class SessionDelete(
        public val requestId: String,
        public val sessionId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("session.compose.get")
    public data class SessionComposeGet(
        public val requestId: String,
        public val sessionId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("session.compose.set")
    public data class SessionComposeSet(
        public val requestId: String,
        public val sessionId: String,
        public val chatMode: String? = null,
        public val toolApprovalMode: String? = null,
        public val chatModel: String? = null,
        public val chatModelProvider: String? = null,
        public val chatModelLabel: String? = null,
        public val reasoningEffort: String? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("context.usage")
    public data class ContextUsage(
        public val requestId: String,
        public val sessionId: String? = null,
        public val draftMessage: String? = null,
        public val modelId: String? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("models.list")
    public data class ModelsList(
        public val requestId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("plan.approve")
    public data class PlanApprove(
        public val requestId: String,
        public val sessionId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("approval.respond")
    public data class ApprovalRespond(
        public val requestId: String,
        public val approvalRequestId: String,
        public val decision: ApprovalDecisionWire,
    ) : ClientMessage()

    @Serializable
    @SerialName("ask.respond")
    public data class AskRespond(
        public val requestId: String,
        public val askRequestId: String,
        public val answer: String,
    ) : ClientMessage()

    /** Reply to server app-level `ping` so tunnel proxies see bidirectional traffic. */
    @Serializable
    @SerialName("pong")
    public data class Pong(
        public val ts: Long,
    ) : ClientMessage()

    @Serializable
    @SerialName("workspace.snapshot")
    public data class WorkspaceSnapshot(
        public val requestId: String,
        public val sessionId: String?,
    ) : ClientMessage()

    @Serializable
    @SerialName("workspace.readFile")
    public data class WorkspaceReadFile(
        public val requestId: String,
        public val path: String,
        public val maxBytes: Int = 200_000,
        public val sessionId: String? = null,
        public val workspaceId: String? = null,
        /** "text" (default) or "download" (one base64 slice; loop until eof). */
        public val mode: String? = null,
        /** Download-mode byte offset; each RPC returns one slice. */
        public val offset: Long? = null,
        /** Requested slice length; desktop caps at 512KB. */
        public val length: Long? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("workspace.files")
    public data class WorkspaceFiles(
        public val requestId: String,
        public val sessionId: String? = null,
        public val workspaceId: String? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("skills.list")
    public data class SkillsList(
        public val requestId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("mcp.list")
    public data class McpList(
        public val requestId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("file.upload.begin")
    public data class FileUploadBegin(
        public val requestId: String,
        public val sessionId: String? = null,
        public val workspaceId: String? = null,
        public val fileName: String,
        public val size: Long,
        public val mime: String? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("file.upload.chunk")
    public data class FileUploadChunk(
        public val requestId: String,
        public val uploadId: String,
        public val offset: Long,
        public val dataBase64: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("file.upload.finish")
    public data class FileUploadFinish(
        public val requestId: String,
        public val uploadId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("file.upload.abort")
    public data class FileUploadAbort(
        public val requestId: String,
        public val uploadId: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("file.download.begin")
    public data class FileDownloadBegin(
        public val requestId: String,
        public val path: String,
        public val sessionId: String? = null,
        public val workspaceId: String? = null,
    ) : ClientMessage()

    public companion object {
        public const val PROTOCOL_VERSION: Int = 1
    }
}

@Serializable
public enum class ApprovalDecisionWire {
    @SerialName("allow_once")
    AllowOnce,

    @SerialName("allow_session")
    AllowSession,

    @SerialName("deny")
    Deny,
}

@Serializable
public sealed class ServerMessage {
    @Serializable
    @SerialName("hello.ok")
    public data class HelloOk(
        public val protocolVersion: Int,
        public val serverName: String = "Anya",
        public val serverVersion: String? = null,
    ) : ServerMessage()

    @Serializable
    @SerialName("hello.error")
    public data class HelloError(
        public val code: String,
        public val message: String,
    ) : ServerMessage()

    @Serializable
    @SerialName("event")
    public data class Event(
        public val name: String,
        public val data: JsonObject,
    ) : ServerMessage()

    @Serializable
    @SerialName("rpc.result")
    public data class RpcResult(
        public val requestId: String,
        public val ok: Boolean,
        public val data: JsonElement? = null,
        public val error: String? = null,
    ) : ServerMessage()

    @Serializable
    @SerialName("ping")
    public data class Ping(
        public val ts: Long,
    ) : ServerMessage()
}
