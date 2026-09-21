package ai.anya.companion.core.data.local

import android.content.Context

private const val PREFS_NAME = "anya_chat_display"
private const val KEY_SHOW_THINKING = "show_thinking"

/**
 * Persist chat transcript display toggles. The reasoning text stays hidden on phones
 * unless the user opts in — see [ai.anya.companion.core.domain.repository.ChatDisplayRepository].
 */
public object ChatDisplayStore {
    public fun readShowThinking(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_THINKING, false)

    public fun writeShowThinking(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_THINKING, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
