package ai.anya.companion.core.data.repository

import android.content.Context
import ai.anya.companion.core.data.local.SessionsDisplayStore
import ai.anya.companion.core.domain.repository.WorkspaceExpansionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
public class DefaultWorkspaceExpansionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkspaceExpansionRepository {
    private val _expanded = MutableStateFlow(SessionsDisplayStore.readExpandedWorkspaceIds(context))
    override val expandedWorkspaceIds: StateFlow<Set<String>> = _expanded.asStateFlow()

    override fun currentExpandedWorkspaceIds(): Set<String> = _expanded.value

    override fun setExpanded(workspaceId: String, expanded: Boolean) {
        _expanded.update { current ->
            if (expanded) current + workspaceId else current - workspaceId
        }
        SessionsDisplayStore.writeExpandedWorkspaceIds(context, _expanded.value)
    }
}
