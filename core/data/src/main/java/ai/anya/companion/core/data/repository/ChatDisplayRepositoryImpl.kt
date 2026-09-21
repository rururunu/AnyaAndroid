package ai.anya.companion.core.data.repository

import android.content.Context
import ai.anya.companion.core.data.local.ChatDisplayStore
import ai.anya.companion.core.domain.repository.ChatDisplayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
public class DefaultChatDisplayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatDisplayRepository {
    private val _showThinking = MutableStateFlow(ChatDisplayStore.readShowThinking(context))
    override val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    override fun currentShowThinking(): Boolean = _showThinking.value

    override fun setShowThinking(enabled: Boolean) {
        ChatDisplayStore.writeShowThinking(context, enabled)
        _showThinking.value = enabled
    }
}
