package ai.anya.companion.core.data.local

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import ai.anya.companion.core.model.settings.AppLanguage
import java.util.Locale

private const val PREFS_NAME = "anya_locale"
private const val KEY_LANGUAGE = "language"

/** Apply the stored in-app language to a Context (call from attachBaseContext). */
public object AppLocale {
    public fun wrap(base: Context): Context {
        val language = read(base)
        if (language == AppLanguage.System) {
            return base
        }
        val locale = language.toJavaLocale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    public fun read(context: Context): AppLanguage {
        val raw = prefs(context).getString(KEY_LANGUAGE, AppLanguage.System.stored)
        return AppLanguage.fromStored(raw)
    }

    public fun write(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.stored).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun AppLanguage.toJavaLocale(): Locale = when (this) {
    AppLanguage.English -> Locale.ENGLISH
    AppLanguage.Chinese -> Locale.SIMPLIFIED_CHINESE
    AppLanguage.System -> Locale.getDefault()
}
