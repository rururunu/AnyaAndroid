package ai.anya.companion.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.domain.repository.LocaleRepository
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.core.model.settings.AppLanguage
import ai.anya.companion.core.model.update.formatDisplayVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class SettingsUiState(
    public val connectionState: ConnectionState = ConnectionState.Disconnected,
    public val credential: DeviceCredential? = null,
    public val pairedDevices: List<DeviceCredential> = emptyList(),
    public val language: AppLanguage = AppLanguage.System,
    public val availableUpdateVersion: String? = null,
)

@HiltViewModel
public class SettingsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val localeRepository: LocaleRepository,
    updateMonitor: AppUpdateMonitor,
) : ViewModel() {

    public val state: StateFlow<SettingsUiState> = combine(
        connectionRepository.connectionState,
        connectionRepository.credential,
        connectionRepository.pairedDevices,
        localeRepository.language,
        updateMonitor.badgeVersion,
    ) { connection, credential, devices, language, badgeVersion ->
        SettingsUiState(
            connectionState = connection,
            credential = credential,
            pairedDevices = devices,
            language = language,
            availableUpdateVersion = badgeVersion?.let(::formatDisplayVersion),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    public fun connect() {
        viewModelScope.launch { connectionRepository.connect() }
    }

    public fun disconnect() {
        viewModelScope.launch { connectionRepository.disconnect() }
    }

    public fun switchDevice(deviceId: String) {
        viewModelScope.launch { connectionRepository.switchDevice(deviceId) }
    }

    public fun renameDevice(deviceId: String, displayName: String) {
        viewModelScope.launch { connectionRepository.renameDevice(deviceId, displayName) }
    }

    public fun removeDevice(deviceId: String) {
        viewModelScope.launch { connectionRepository.removeDevice(deviceId) }
    }

    public fun unpair() {
        viewModelScope.launch { connectionRepository.clearPairing() }
    }

    public fun setLanguage(language: AppLanguage) {
        localeRepository.setLanguage(language)
    }
}
