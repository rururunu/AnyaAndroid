package ai.anya.companion.core.model.workspace

import kotlinx.serialization.Serializable

/** Desktop `get_environment_context` / `context.environment` payload (subset used by Companion). */
@Serializable
public data class EnvironmentContext(
    public val selection: String? = null,
    public val selectedFiles: List<String> = emptyList(),
    public val selectedImages: List<String> = emptyList(),
    public val activeWindow: String? = null,
    public val activeFile: String? = null,
    public val workspace: EnvironmentWorkspace? = null,
    public val clipboard: String? = null,
    public val gitStatus: String? = null,
    public val lastShellExecution: String? = null,
    public val ideContext: IdeContextSummary? = null,
    public val officeContext: OfficeContextSummary? = null,
)

@Serializable
public data class EnvironmentWorkspace(
    public val name: String = "",
    public val root: String = "",
)

@Serializable
public data class IdeContextSummary(
    public val ide: String? = null,
    public val language: String? = null,
    public val workspace: String? = null,
    public val activeFile: String? = null,
    public val selection: String? = null,
)

@Serializable
public data class OfficeContextSummary(
    public val app: String? = null,
    public val documentPath: String? = null,
    public val documentName: String? = null,
    public val selectedText: String? = null,
)
