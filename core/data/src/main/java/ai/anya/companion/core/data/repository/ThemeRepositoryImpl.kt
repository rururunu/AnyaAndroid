package ai.anya.companion.core.data.repository

import android.content.Context
import ai.anya.companion.core.data.local.AppThemeStore
import ai.anya.companion.core.domain.repository.ThemeRepository
import ai.anya.companion.core.model.settings.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
public class DefaultThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemeRepository {
    private val _theme = MutableStateFlow(AppThemeStore.read(context))
    override val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    override fun current(): AppTheme = _theme.value

    override fun setTheme(theme: AppTheme) {
        AppThemeStore.write(context, theme)
        _theme.value = theme
    }
}
