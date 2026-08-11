package ai.anya.companion.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.usecase.ConnectGatewayUseCase
import ai.anya.companion.core.domain.usecase.PairDeviceUseCase
import ai.anya.companion.core.model.protocol.PairingPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class PairingUiState(
    public val host: String = "",
    public val port: String = "8787",
    public val token: String = "",
    public val scheme: String = "ws",
    public val isSubmitting: Boolean = false,
    public val error: String? = null,
    public val paired: Boolean = false,
)

@HiltViewModel
public class PairingViewModel @Inject constructor(
    private val pairDevice: PairDeviceUseCase,
    private val connectGateway: ConnectGatewayUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    public val state: StateFlow<PairingUiState> = _state.asStateFlow()

    public fun onHostChange(value: String) = _state.update { it.copy(host = value, error = null) }
    public fun onPortChange(value: String) = _state.update { it.copy(port = value, error = null) }
    public fun onTokenChange(value: String) = _state.update { it.copy(token = value, error = null) }

    public fun applyPairLink(raw: String) {
        val link = parsePairLink(raw)
        if (link == null) {
            // Scanner may return the short code alone.
            val token = normalizePairingToken(raw)
            if (token.isNotBlank() && !raw.contains("://")) {
                _state.update { it.copy(token = token, error = null) }
            } else {
                _state.update { it.copy(error = "无法识别的配对二维码") }
            }
            return
        }
        _state.update {
            it.copy(
                host = link.host,
                port = link.port.toString(),
                token = link.token,
                scheme = link.scheme,
                error = null,
            )
        }
    }

    public fun submit() {
        val current = _state.value
        val port = current.port.toIntOrNull()
        val token = normalizePairingToken(current.token)
        if (current.host.isBlank() || port == null || token.isBlank()) {
            _state.update { it.copy(error = "请填写主机、端口与配对令牌") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val payload = PairingPayload(
                host = current.host.trim(),
                port = port,
                pairingToken = token,
                scheme = current.scheme.ifBlank { "ws" },
            )
            when (val paired = pairDevice(payload)) {
                is AnyaResult.Failure -> {
                    _state.update {
                        it.copy(isSubmitting = false, error = paired.error.toString())
                    }
                }
                is AnyaResult.Success -> {
                    when (val connected = connectGateway()) {
                        is AnyaResult.Failure -> _state.update {
                            it.copy(isSubmitting = false, paired = true, error = connected.error.toString())
                        }
                        is AnyaResult.Success -> _state.update {
                            it.copy(isSubmitting = false, paired = true)
                        }
                    }
                }
            }
        }
    }
}
