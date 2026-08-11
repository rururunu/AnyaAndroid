package ai.anya.companion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RootUiState(
    val hasCredential: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** True while we still expect an automatic first connect after cold start. */
    val bootConnecting: Boolean = false,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    connectionRepository: ConnectionRepository,
) : ViewModel() {
    val hasCredential: StateFlow<Boolean> = connectionRepository.credential
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val state: StateFlow<RootUiState> = combine(
        connectionRepository.credential,
        connectionRepository.connectionState,
    ) { credential, connection ->
        val hasCred = credential != null
        val bootConnecting = hasCred && (
            connection == ConnectionState.Connecting ||
                connection == ConnectionState.Reconnecting ||
                connection == ConnectionState.Disconnected
            )
        RootUiState(
            hasCredential = hasCred,
            connectionState = connection,
            bootConnecting = bootConnecting,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())
}
