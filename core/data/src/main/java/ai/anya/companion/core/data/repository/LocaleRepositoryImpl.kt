package ai.anya.companion.core.data.repository

import android.content.Context
import ai.anya.companion.core.data.local.AppLocale
import ai.anya.companion.core.domain.repository.LocaleRepository
import ai.anya.companion.core.model.settings.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
public class DefaultLocaleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocaleRepository {
    private val _language = MutableStateFlow(AppLocale.read(context))
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    override fun current(): AppLanguage = _language.value

    override fun setLanguage(language: AppLanguage) {
        AppLocale.write(context, language)
        _language.value = language
    }
}
