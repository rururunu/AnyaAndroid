package ai.anya.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.model.session.ToolActivity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/* ------------------------------ toolDiff.ts port ------------------------------ */

internal enum class DiffLineKind { Context, Addition, Deletion }

internal data class DiffLine(
    val kind: DiffLineKind,
    val oldNo: Int? = null,
    val newNo: Int? = null,
    val text: String,
)

internal data class DiffHunk(
    val oldStart: Int,
    val newStart: Int,
    val lines: List<DiffLine>,
    val added: Int,
    val removed: Int,
)

private val HUNK_HEADER = Regex("^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@")

/** Parse a unified diff into hunks with interleaved line numbers. */
internal fun parseUnifiedDiffHunks(diff: String): List<DiffHunk> {
    val hunks = mutableListOf<DiffHunk>()
    var lines: MutableList<DiffLine>? = null
    var oldStart = 1
    var newStart = 1
    var oldNo = 0
    var newNo = 0
    var added = 0
    var removed = 0
    fun flush() {
        val current = lines
        if (current != null && current.isNotEmpty()) {
            hunks += DiffHunk(oldStart, newStart, current.toList(), added, removed)
        }
        lines = null
    }
    for (raw in diff.replace("\r\n", "\n").split('\n')) {
        val header = HUNK_HEADER.find(raw)
        if (header != null) {
            flush()
            oldNo = header.groupValues[1].toInt()
            newNo = header.groupValues[2].toInt()
            oldStart = oldNo
            newStart = newNo
            lines = mutableListOf()
            added = 0
            removed = 0
            continue
        }
        if (raw.startsWith("--- ") || raw.startsWith("+++ ") || raw.startsWith("diff ") ||
            raw.startsWith("index ") || raw.startsWith("\\")
        ) {
            continue
        }
        if (lines == null) {
            lines = mutableListOf()
            oldStart = 1
            newStart = 1
            oldNo = 1
            newNo = 1
            added = 0
            removed = 0
        }
        val current = lines!!
        when {
            raw.startsWith("-") -> { current += DiffLine(DiffLineKind.Deletion, oldNo = oldNo, text = raw.substring(1)); removed++; oldNo++ }
            raw.startsWith("+") -> { current += DiffLine(DiffLineKind.Addition, newNo = newNo, text = raw.substring(1)); added++; newNo++ }
            raw.startsWith(" ") || raw.isEmpty() -> {
                current += DiffLine(DiffLineKind.Context, oldNo = oldNo, newNo = newNo, text = if (raw.startsWith(" ")) raw.substring(1) else raw)
                oldNo++
                newNo++
            }
        }
    }
    flush()
    return hunks
}

private fun splitLines(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    val lines = value.replace("\r\n", "\n").split('\n').toMutableList()
    if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
    return lines
}

/** Single hunk from plain old/new text (no context). */
internal fun hunkFromPlainEdit(oldText: String, newText: String): DiffHunk {
    val oldLines = splitLines(oldText)
    val newLines = splitLines(newText)
    val lines = oldLines.mapIndexed { i, t -> DiffLine(DiffLineKind.Deletion, oldNo = i + 1, text = t) } +
        newLines.mapIndexed { i, t -> DiffLine(DiffLineKind.Addition, newNo = i + 1, text = t) }
    return DiffHunk(1, 1, lines, added = newLines.size, removed = oldLines.size)
}

private fun argString(activity: ToolActivity, key: String): String =
    activity.arguments?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

/** Desktop `collectActivityHunks`: prefer the server diff, else rebuild from tool arguments. */
internal fun collectActivityHunks(activity: ToolActivity): List<DiffHunk> {
    if (activity.kind == "shell") return emptyList()
    val preview = activity.preview
    if (!preview?.unifiedDiff.isNullOrBlank()) return parseUnifiedDiffHunks(preview!!.unifiedDiff)
    if (activity.toolName == "replace_many_in_file") {
        val edits = activity.arguments?.get("edits") as? JsonArray
        if (edits != null) {
            return edits.mapNotNull { value ->
                val edit = value as? JsonObject ?: return@mapNotNull null
                hunkFromPlainEdit(
                    edit["old_string"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    edit["new_string"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }
    }
    return when {
        activity.toolName == "replace_in_file" ->
            listOf(hunkFromPlainEdit(argString(activity, "old_string"), argString(activity, "new_string")))
        activity.toolName == "write_file" -> listOf(hunkFromPlainEdit("", argString(activity, "content")))
        activity.kind == "delete" -> {
            val deleted = argString(activity, "old_string").ifEmpty { argString(activity, "symbol") }
                .ifEmpty { argString(activity, "start_anchor") }
                .ifEmpty { activity.detail.orEmpty() }
            listOf(hunkFromPlainEdit(deleted, ""))
        }
        else -> emptyList()
    }
}

internal fun fileBasename(path: String?): String {
    val normalized = path.orEmpty().replace('\\', '/')
    return normalized.substringAfterLast('/').ifEmpty { path ?: "file" }
}

internal fun fileParentDir(path: String?): String {
    val normalized = path.orEmpty().replace('\\', '/')
    val sep = normalized.lastIndexOf('/')
    return if (sep > 0) normalized.substring(0, sep) else ""
}

/* ------------------------------ FileDiffCard ------------------------------ */

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "create" -> Icons.Rounded.NoteAdd
    "delete" -> Icons.Rounded.DeleteOutline
    "move" -> Icons.AutoMirrored.Rounded.ArrowForward
    "edit" -> Icons.Rounded.Edit
    else -> Icons.Rounded.Description
}

private fun kindLabel(kind: String): String = when (kind) {
    "create" -> "新建"
    "delete" -> "删除"
    "move" -> "移动"
    else -> "编辑"
}

/**
 * One file operation with its hunks inline, like the desktop FileDiffCard: header
 * (kind · name · dir · +N −M) collapsed by default; expanding lists coloured lines
 * with numbers. The expand icon opens the fullscreen viewer for long diffs.
 */
@Composable
internal fun FileDiffCard(
    activity: ToolActivity,
    hunks: List<DiffHunk>,
    onOpenDiff: (DiffViewRequest) -> Unit,
) {
    val path = activity.preview?.path?.takeIf { it.isNotBlank() }
        ?: argString(activity, "path").ifEmpty { activity.title }
    val running = activity.status == "running"
    val failed = activity.status == "error" || !activity.success
    val added = hunks.sumOf { it.added }
    val removed = hunks.sumOf { it.removed }
    var expanded by rememberSaveable(activity.id) { mutableStateOf(false) }
    val haptics = rememberAnyaHaptics()
    val fullDiff = activity.preview?.unifiedDiff?.takeIf { it.isNotBlank() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hunks.isNotEmpty()) {
                        haptics.tick()
                        expanded = !expanded
                    }
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (hunks.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = kindIcon(activity.kind),
                    contentDescription = kindLabel(activity.kind),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fileBasename(path),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val dir = fileParentDir(path)
                if (dir.isNotEmpty()) {
                    Text(
                        text = dir,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                when {
                    running -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    failed -> Text("失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    else -> {
                        if (added > 0) Text("+$added", style = MaterialTheme.typography.labelSmall, color = DiffColors.addedText)
                        if (removed > 0) Text("−$removed", style = MaterialTheme.typography.labelSmall, color = DiffColors.removedText)
                    }
                }
                if (fullDiff != null) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "全屏查看",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                haptics.tick()
                                onOpenDiff(DiffViewRequest(path = path, unifiedDiff = fullDiff))
                            }
                            .padding(5.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && hunks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                ) {
                    hunks.forEachIndexed { index, hunk ->
                        if (index > 0) {
                            Text(
                                text = "···",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            )
                        }
                        hunk.lines.forEach { line -> DiffLineRow(line) }
                    }
                }
            }
        }
    }
}

private object DiffColors {
    val addedText = ai.anya.companion.core.designsystem.theme.AnyaColors.DiffAdded
    val removedText = ai.anya.companion.core.designsystem.theme.AnyaColors.DiffRemoved
    val addedBg = ai.anya.companion.core.designsystem.theme.AnyaColors.DiffAddedBackground
    val removedBg = ai.anya.companion.core.designsystem.theme.AnyaColors.DiffRemovedBackground
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val bg = when (line.kind) {
        DiffLineKind.Addition -> DiffColors.addedBg
        DiffLineKind.Deletion -> DiffColors.removedBg
        DiffLineKind.Context -> Color.Transparent
    }
    val marker = when (line.kind) {
        DiffLineKind.Addition -> "+"
        DiffLineKind.Deletion -> "-"
        DiffLineKind.Context -> " "
    }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 17.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.oldNo?.toString() ?: "",
            style = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.width(30.dp),
        )
        Text(
            text = line.newNo?.toString() ?: "",
            style = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.width(30.dp),
        )
        Text(
            text = marker,
            style = mono,
            color = when (line.kind) {
                DiffLineKind.Addition -> DiffColors.addedText
                DiffLineKind.Deletion -> DiffColors.removedText
                DiffLineKind.Context -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(12.dp),
        )
        Text(
            text = line.text,
            style = mono,
            color = MaterialTheme.colorScheme.onBackground,
            softWrap = false,
        )
    }
}
