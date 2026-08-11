package ai.anya.companion.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.protocol.DeviceCredential
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
)

@HiltViewModel
public class SettingsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    public val state: StateFlow<SettingsUiState> = combine(
        connectionRepository.connectionState,
        connectionRepository.credential,
    ) { connection, credential ->
        SettingsUiState(connectionState = connection, credential = credential)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    public fun connect() {
        viewModelScope.launch { connectionRepository.connect() }
    }

    public fun disconnect() {
        viewModelScope.launch { connectionRepository.disconnect() }
    }

    public fun unpair() {
        viewModelScope.launch { connectionRepository.clearPairing() }
    }
}
