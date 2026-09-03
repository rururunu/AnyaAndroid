package ai.anya.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.SharedFileStatus
import ai.anya.companion.core.model.session.ToolActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One picture produced by the desktop `generate_image` tool. */
internal data class GeneratedImageRef(
    /** Path the desktop wrote first (durable app-data copy). */
    val path: String,
    /** Copy inside the bound workspace (`.anya/generated/…`), reachable by the phone. */
    val workspacePath: String? = null,
    val revisedPrompt: String? = null,
) {
    val fileName: String
        get() = path.replace('\\', '/').substringAfterLast('/')

    /** Path to request from the gateway; workspace copy first since the gateway is workspace-scoped. */
    val fetchPath: String
        get() = workspacePath ?: path
}

/**
 * Hooks the generated-image card needs from the screen: fetch bytes through the
 * gateway, look up an already-cached copy, and open it fullscreen / start an edit.
 */
@Immutable
internal data class GeneratedImageActions(
    val fetch: (path: String) -> Unit = {},
    val resolveLocal: (path: String) -> ChatSharedFile? = { null },
    val preview: (ChatSharedFile) -> Unit = {},
    val editFromImage: (desktopPath: String) -> Unit = {},
)

internal val LocalGeneratedImageActions = staticCompositionLocalOf { GeneratedImageActions() }

private val ANYA_IMAGES_FENCE = Regex("```anya-images\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
/** `![alt](path:…)` or `![alt](C:\…)` / `![alt](/…)` — images the desktop wrote to its own disk. */
internal val MARKDOWN_LOCAL_IMAGE =
    Regex("!\\[([^\\]]*)]\\((path:[^)\\s]+|(?:[a-zA-Z]:[\\\\/]|/|\\\\\\\\)[^)\\s]+)\\)")
private val WORKSPACE_COPY_LINE = Regex("^Workspace copy:\\s*(.+)$", RegexOption.MULTILINE)
private val SAVED_LINE = Regex("^Saved:\\s+(.+)$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

internal fun isGeneratedImageActivity(activity: ToolActivity): Boolean =
    activity.kind == "image" || activity.toolName == "generate_image"

private fun stripPathPrefix(raw: String): String {
    val value = raw.trim()
    return if (value.startsWith("path:")) value.removePrefix("path:") else value
}

/**
 * Mirrors desktop `parseGeneratedImageSources`: prefer the trailing `anya-images`
 * JSON fence, fall back to `![alt](path:…)` markdown, and pair each image with the
 * `Workspace copy:` line that shares its file name.
 */
internal fun parseGeneratedImages(result: String?): List<GeneratedImageRef> {
    val text = result?.takeIf { it.isNotBlank() } ?: return emptyList()
    val workspaceCopies = WORKSPACE_COPY_LINE.findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .associateBy { it.replace('\\', '/').substringAfterLast('/') }

    val refs = LinkedHashMap<String, GeneratedImageRef>()
    fun add(path: String, revised: String?) {
        val clean = stripPathPrefix(path)
        if (clean.isEmpty() || clean in refs) return
        val name = clean.replace('\\', '/').substringAfterLast('/')
        refs[clean] = GeneratedImageRef(
            path = clean,
            workspacePath = workspaceCopies[name],
            revisedPrompt = revised?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    val fence = ANYA_IMAGES_FENCE.find(text)?.groupValues?.getOrNull(1)?.trim()
    if (!fence.isNullOrEmpty()) {
        runCatching {
            val obj = Json.parseToJsonElement(fence) as? JsonObject ?: return@runCatching
            obj["images"]?.jsonArray?.forEach { item ->
                val entry = item as? JsonObject ?: return@forEach
                val path = entry["path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val revised = entry["revised_prompt"]?.jsonPrimitive?.contentOrNull
                    ?: entry["revisedPrompt"]?.jsonPrimitive?.contentOrNull
                add(path, revised)
            }
        }
    }
    if (refs.isEmpty()) {
        MARKDOWN_LOCAL_IMAGE.findAll(text).forEach { match ->
            add(match.groupValues[2], match.groupValues[1].takeIf { it.isNotBlank() })
        }
    }
    if (refs.isEmpty()) {
        SAVED_LINE.findAll(text).forEach { match -> add(match.groupValues[1], null) }
    }
    return refs.values.toList()
}

/**
 * Generated images referenced anywhere in a turn (tool results), keyed by file name.
 * Lets reply markdown that points at the desktop's app-data copy resolve to the
 * workspace copy the gateway can actually serve — and skip duplicate tiles.
 */
internal fun collectGeneratedImageRefs(activities: List<ToolActivity>): Map<String, GeneratedImageRef> {
    val out = LinkedHashMap<String, GeneratedImageRef>()
    activities.asSequence()
        .filter(::isGeneratedImageActivity)
        .flatMap { parseGeneratedImages(it.result).asSequence() }
        .forEach { ref -> out.putIfAbsent(ref.fileName, ref) }
    return out
}

private fun samePath(a: String, b: String): Boolean =
    a.replace('\\', '/').trimEnd('/').equals(b.replace('\\', '/').trimEnd('/'), ignoreCase = true)

/**
 * `generate_image` result — matches desktop: no bulky header chrome, just the media
 * (and a compact status line while running / on failure).
 */
@Composable
internal fun GeneratedImageToolCard(activity: ToolActivity) {
    val running = activity.status == "running"
    val failed = activity.status == "error" || !activity.success
    val images = remember(activity.result) { parseGeneratedImages(activity.result) }

    when {
        running && images.isEmpty() -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Brush,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "正在生图…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        failed && images.isEmpty() -> {
            Text(
                text = activity.result.orEmpty().lineSequence().firstOrNull()
                    ?: activity.detail?.lineSequence()?.firstOrNull()
                    ?: "生图失败",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        else -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                images.forEach { image ->
                    GeneratedImageTile(image = image)
                }
            }
        }
    }
}

/** One generated picture — desktop-like frame + edit / expand actions. */
@Composable
internal fun GeneratedImageTile(image: GeneratedImageRef) {
    val haptic = rememberAnyaHaptics()
    val actions = LocalGeneratedImageActions.current
    val local = actions.resolveLocal(image.fetchPath) ?: actions.resolveLocal(image.path)
    val ready = local?.status == SharedFileStatus.Ready && !local.localPath.isNullOrBlank()
    val pending = local?.status == SharedFileStatus.Pending
    val failed = local?.status == SharedFileStatus.Failed
    val canEdit = isDesktopAbsolutePath(image.path)

    LaunchedEffect(image.fetchPath, image.path, ready, pending) {
        if (!ready && !pending) {
            actions.fetch(image.fetchPath)
        }
    }

    Column(
        modifier = Modifier.widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
        ) {
            when {
                ready -> {
                    SharedImagePreview(
                        path = local!!.localPath!!,
                        contentDescription = image.revisedPrompt ?: image.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clickable {
                                haptic.buttonPress()
                                actions.preview(local)
                            },
                    )
                    // Expand affordance — mirrors desktop overlay.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable {
                                haptic.buttonPress()
                                actions.preview(local)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "预览",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .clickable(enabled = !pending) {
                                haptic.buttonPress()
                                actions.fetch(image.fetchPath)
                            }
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            pending || (!ready && !failed) -> CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "获取失败，点击重试",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (ready && canEdit) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        haptic.buttonPress()
                        actions.editFromImage(image.path)
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(text = "基于此图修改", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = {
                        haptic.buttonPress()
                        local?.let(actions.preview)
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(text = "涂抹修改", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Finds a cached copy of [path] among the chat's shared-file cards / preview cache. */
internal fun findLocalSharedImage(
    files: Sequence<ChatSharedFile>,
    path: String,
): ChatSharedFile? {
    val name = path.replace('\\', '/').substringAfterLast('/')
    return files.firstOrNull { samePath(it.path, path) }
        ?: files.firstOrNull { it.status == SharedFileStatus.Ready && it.name == name }
}
