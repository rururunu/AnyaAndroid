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
)

@Serializable
public data class DeviceCredential(
    public val deviceId: String,
    public val credential: String,
    public val host: String,
    public val port: Int,
    public val scheme: String = "wss",
    public val pairedAtEpochMs: Long,
)

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
