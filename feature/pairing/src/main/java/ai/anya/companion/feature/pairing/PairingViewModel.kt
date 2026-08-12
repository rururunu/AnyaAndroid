package ai.anya.companion.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.common.result.AnyaError
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
    public val info: String? = null,
    public val paired: Boolean = false,
)

@HiltViewModel
public class PairingViewModel @Inject constructor(
    private val pairDevice: PairDeviceUseCase,
    private val connectGateway: ConnectGatewayUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    public val state: StateFlow<PairingUiState> = _state.asStateFlow()

    public fun onHostChange(value: String) =
        _state.update { it.copy(host = value, error = null, info = null) }

    public fun onPortChange(value: String) =
        _state.update { it.copy(port = value, error = null, info = null) }

    public fun onTokenChange(value: String) =
        _state.update { it.copy(token = value, error = null, info = null) }

    public fun onCameraPermissionDenied() {
        _state.update {
            it.copy(
                error = "未授予相机权限，可在下方手动填写配对信息",
                info = null,
            )
        }
    }

    public fun applyPairLink(raw: String, autoSubmit: Boolean = true) {
        val link = parsePairLink(raw)
        if (link == null) {
            val token = normalizePairingToken(raw)
            if (token.isNotBlank() && !raw.contains("://")) {
                _state.update {
                    it.copy(
                        token = token,
                        error = null,
                        info = "已识别配对码，请填写主机后连接",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        error = "无法识别该二维码，请扫描桌面「连接手机」中的配对码",
                        info = null,
                    )
                }
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
                info = "已识别 ${link.host}，正在连接…",
            )
        }
        if (autoSubmit) submit()
    }

    public fun submit() {
        val current = _state.value
        val port = current.port.toIntOrNull()
        val token = normalizePairingToken(current.token)
        if (current.host.isBlank() || port == null || token.isBlank()) {
            _state.update {
                it.copy(
                    isSubmitting = false,
                    error = "请填写主机、端口与配对令牌",
                    info = null,
                )
            }
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
                        it.copy(
                            isSubmitting = false,
                            error = paired.error.toUserMessage(),
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
                                error = connected.error.toUserMessage(),
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
}

private fun AnyaError.toUserMessage(): String = when (this) {
    is AnyaError.Network -> when {
        message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ->
            "连接超时，请确认桌面端在线，且公网隧道已开启"
        message.contains("hello", ignoreCase = true) ||
            message.contains("Unauthorized", ignoreCase = true) ->
            "配对被拒绝，请在桌面刷新二维码后重试"
        message.contains("Failed to open", ignoreCase = true) ||
            message.contains("Unable to resolve", ignoreCase = true) ->
            "无法连上主机，请检查网络或 Hostname"
        else -> message.ifBlank { "无法连接桌面端" }
    }
    is AnyaError.Unauthorized -> "配对令牌无效或已过期，请重新扫码"
    is AnyaError.NotPaired -> "尚未配对"
    is AnyaError.Protocol -> message.ifBlank { "协议错误，请更新应用后重试" }
    is AnyaError.Unknown -> message.ifBlank { "配对失败，请重试" }
}
