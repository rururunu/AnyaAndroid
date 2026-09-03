package ai.anya.companion.core.data.local

import android.content.Context
import ai.anya.companion.core.model.settings.AppTheme

private const val PREFS_NAME = "anya_appearance"
private const val KEY_THEME = "theme"

/** Persist the in-app color scheme (system / light / dark). */
public object AppThemeStore {
    public fun read(context: Context): AppTheme {
        val raw = prefs(context).getString(KEY_THEME, AppTheme.System.stored)
        return AppTheme.fromStored(raw)
    }

    public fun write(context: Context, theme: AppTheme) {
        prefs(context).edit().putString(KEY_THEME, theme.stored).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
