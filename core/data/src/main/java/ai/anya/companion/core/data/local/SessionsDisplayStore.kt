package ai.anya.companion.core.data.local

import android.content.Context

private const val PREFS_NAME = "anya_sessions_display"
private const val KEY_EXPANDED_WORKSPACES = "expanded_workspaces"

/**
 * Persist which workspace groups the user left expanded on the sessions screen. Nothing is written
 * until the user opens a group, so every workspace starts collapsed — see
 * [ai.anya.companion.core.domain.repository.WorkspaceExpansionRepository].
 */
public object SessionsDisplayStore {
    public fun readExpandedWorkspaceIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXPANDED_WORKSPACES, emptySet()).orEmpty().toSet()

    public fun writeExpandedWorkspaceIds(context: Context, ids: Set<String>) {
        // Copy: SharedPreferences must not keep a reference the caller can mutate afterwards.
        prefs(context).edit().putStringSet(KEY_EXPANDED_WORKSPACES, ids.toSet()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
