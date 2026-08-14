package ai.anya.companion.feature.pairing

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.common.result.AnyaError
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.usecase.ConnectGatewayUseCase
import ai.anya.companion.core.domain.usecase.PairDeviceUseCase
import ai.anya.companion.core.model.protocol.HostDisplayName
import ai.anya.companion.core.model.protocol.PairingPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class PairingUiState(
    public val host: String = "",
    public val port: String = DefaultGatewayPort.toString(),
    public val token: String = "",
    public val scheme: String = "ws",
    public val lanHost: String? = null,
    public val lanPort: Int? = null,
    public val displayName: String = "",
    public val replaceDeviceId: String? = null,
    public val isSubmitting: Boolean = false,
    public val error: String? = null,
    public val info: String? = null,
    public val paired: Boolean = false,
)

@HiltViewModel
public class PairingViewModel @Inject constructor(
    private val pairDevice: PairDeviceUseCase,
    private val connectGateway: ConnectGatewayUseCase,
    private val connectionRepository: ConnectionRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    public val state: StateFlow<PairingUiState> = _state.asStateFlow()

    init {
        val repairId = savedStateHandle.get<String>("repairDeviceId")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val existing = repairId?.let { id ->
            connectionRepository.pairedDevices.value.find { it.deviceId == id }
        }
        if (existing != null) {
            _state.update {
                it.copy(
                    host = existing.host,
                    port = existing.port.toString(),
                    scheme = existing.scheme,
                    lanHost = existing.lanHost,
                    lanPort = existing.lanPort,
                    displayName = existing.resolvedDisplayName(),
                    replaceDeviceId = existing.deviceId,
                )
            }
        }
    }

    public fun onHostChange(value: String) =
        _state.update { it.copy(host = value, error = null, info = null) }

    public fun onPortChange(value: String) =
        _state.update { it.copy(port = value, error = null, info = null) }

    public fun onTokenChange(value: String) =
        _state.update { it.copy(token = value, error = null, info = null) }

    public fun onDisplayNameChange(value: String) =
        _state.update {
            it.copy(
                displayName = HostDisplayName.sanitize(value),
                error = null,
                info = null,
            )
        }

    public fun onSchemeChange(value: String) {
        val scheme = when (value.lowercase()) {
            "wss" -> "wss"
            else -> "ws"
        }
        _state.update { current ->
            val lanDefault = DefaultGatewayPort.toString()
            val port = when {
                scheme == "wss" && current.port in listOf("", "80", lanDefault) -> "443"
                scheme == "ws" && current.port in listOf("", "443") -> lanDefault
                else -> current.port
            }
            current.copy(scheme = scheme, port = port, error = null, info = null)
        }
    }

    public fun onCameraPermissionDenied() {
        _state.update {
            it.copy(
                error = context.getString(R.string.pairing_camera_denied),
                info = null,
            )
        }
    }

    public fun applyPairLink(raw: String, autoSubmit: Boolean = false) {
        val link = parsePairLink(raw)
        if (link == null) {
            val token = normalizePairingToken(raw)
            if (token.isNotBlank() && !raw.contains("://")) {
                _state.update {
                    it.copy(
                        token = token,
                        error = null,
                        info = context.getString(R.string.pairing_token_need_host),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        error = context.getString(R.string.pairing_qr_unrecognized),
                        info = null,
                    )
                }
            }
            return
        }
        val current = _state.value
        val matched = matchStoredDevice(link.host, link.lanHost, current.replaceDeviceId)
        val displayName = current.displayName.ifBlank {
            HostDisplayName.suggest(
                host = link.host,
                lanHost = link.lanHost,
                existing = matched?.displayName,
            )
        }
        _state.update {
            it.copy(
                host = link.host,
                port = link.port.toString(),
                token = link.token,
                scheme = link.scheme,
                lanHost = link.lanHost,
                lanPort = link.lanPort,
                displayName = displayName,
                replaceDeviceId = current.replaceDeviceId ?: matched?.deviceId,
                error = null,
                info = context.getString(R.string.pairing_recognized_confirm, displayName),
            )
        }
        if (autoSubmit) submit()
    }

    public fun submit() {
        val current = _state.value
        val token = normalizePairingToken(current.token)
        // Users often paste the whole ws://host:port/remote/v1 link into the host
        // field; extract host/port/scheme instead of shipping the URL as a hostname.
        val hostInput = parseManualHostInput(current.host)
        val port = when {
            hostInput?.port != null -> hostInput.port
            // A pasted URL without explicit port implies the scheme default,
            // not whatever is left in the port field.
            hostInput?.scheme == "wss" -> 443
            hostInput?.scheme == "ws" -> 80
            else -> current.port.toIntOrNull()
        }
        if (hostInput == null || port == null || token.isBlank()) {
            _state.update {
                it.copy(
                    isSubmitting = false,
                    error = context.getString(R.string.pairing_fill_required),
                    info = null,
                )
            }
            return
        }
        val scheme = hostInput.scheme ?: current.scheme.ifBlank { "ws" }
        val displayName = HostDisplayName.orFallback(
            current.displayName.ifBlank {
                HostDisplayName.suggest(
                    host = hostInput.host,
                    lanHost = current.lanHost,
                )
            },
        )
        viewModelScope.launch {
            _state.update {
                it.copy(
                    host = hostInput.host,
                    port = port.toString(),
                    scheme = scheme,
                    displayName = displayName,
                    isSubmitting = true,
                    error = null,
                )
            }
            val payload = PairingPayload(
                host = hostInput.host,
                port = port,
                pairingToken = token,
                scheme = scheme,
                lanHost = current.lanHost,
                lanPort = current.lanPort,
                displayName = displayName,
            )
            when (val paired = pairDevice(payload, current.replaceDeviceId)) {
                is AnyaResult.Failure -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = paired.error.toUserMessage(context),
                            info = null,
                        )
                    }
                }
                is AnyaResult.Success -> {
                    when (val connected = connectGateway()) {
                        is AnyaResult.Failure -> _state.update {
                            it.copy(
                                isSubmitting = false,
                                paired = true,
                                error = connected.error.toUserMessage(context),
                                info = null,
                            )
                        }
                        is AnyaResult.Success -> _state.update {
                            it.copy(isSubmitting = false, paired = true, error = null, info = null)
                        }
                    }
                }
            }
        }
    }

    private fun matchStoredDevice(
        host: String,
        lanHost: String?,
        replaceDeviceId: String?,
    ): ai.anya.companion.core.model.protocol.DeviceCredential? {
        val devices = connectionRepository.pairedDevices.value
        if (!replaceDeviceId.isNullOrBlank()) {
            devices.find { it.deviceId == replaceDeviceId }?.let { return it }
        }
        val lan = lanHost?.trim()?.takeIf { it.isNotEmpty() }
        if (lan != null) {
            devices.find { it.lanHost?.trim().equals(lan, ignoreCase = true) }?.let { return it }
        }
        return devices.find { it.host.equals(host.trim(), ignoreCase = true) }
    }
}

private fun AnyaError.toUserMessage(context: Context): String = when (this) {
    is AnyaError.Network -> when {
        message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ->
            context.getString(R.string.pairing_error_timeout)
        message.contains("hello", ignoreCase = true) ||
            message.contains("Unauthorized", ignoreCase = true) ->
            context.getString(R.string.pairing_error_rejected)
        message.contains("Failed to open", ignoreCase = true) ||
            message.contains("Unable to resolve", ignoreCase = true) ->
            context.getString(R.string.pairing_error_unreachable)
        else -> message.ifBlank { context.getString(R.string.pairing_error_generic) }
    }
    is AnyaError.Unauthorized -> context.getString(R.string.pairing_error_unauthorized)
    is AnyaError.NotPaired -> context.getString(R.string.pairing_error_not_paired)
    is AnyaError.Protocol -> message.ifBlank { context.getString(R.string.pairing_error_protocol) }
    is AnyaError.Unknown -> message.ifBlank { context.getString(R.string.pairing_error_unknown) }
}
