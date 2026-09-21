package ai.anya.companion.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.component.AnyaSheetActionRow
import ai.anya.companion.core.designsystem.component.AnyaSheetCard
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.ToolActivity
import ai.anya.companion.core.model.session.ToolPreviewPayload
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

public enum class DiffScope {
    Round,
    Uncommitted,
    Session,
}

public data class ReviewChange(
    public val id: String,
    public val path: String,
    public val unifiedDiff: String,
    public val added: Int = 0,
    public val removed: Int = 0,
)

/** Messages from the latest user turn through the current assistant reply. */
internal fun lastRoundMessages(messages: List<ChatMessage>): List<ChatMessage> {
    for (index in messages.indices.reversed()) {
        if (messages[index].role == ChatRole.User) return messages.subList(index, messages.size)
    }
    return messages
}

/** Collect done tool diffs from messages (newest file wins on same path). */
internal fun extractReviewChanges(messages: List<ChatMessage>): List<ReviewChange> {
    val byPath = LinkedHashMap<String, ReviewChange>()
    for (message in messages) {
        for (activity in message.toolActivities) {
            if (!activity.success) continue
            if (!activity.status.equals("done", ignoreCase = true)) continue
            for (change in changesForActivity(message.id, activity)) {
                byPath[change.path] = change
            }
        }
    }
    return byPath.values.toList().asReversed()
}

internal fun codeChangesFromUnifiedDiff(diff: String): List<ReviewChange> {
    if (diff.isBlank()) return emptyList()
    val byPath = LinkedHashMap<String, ReviewChange>()
    for ((index, section) in splitUnifiedDiffByFile(diff).withIndex()) {
        if (section.path.isBlank()) continue
        val counts = countDiffLines(section.diff)
        byPath[section.path] = ReviewChange(
            id = "git:$index:${section.path}",
            path = section.path,
            unifiedDiff = section.diff,
            added = counts.first,
            removed = counts.second,
        )
    }
    return byPath.values.toList()
}

private data class DiffSection(val path: String, val diff: String)

private fun splitUnifiedDiffByFile(diff: String): List<DiffSection> {
    val sections = mutableListOf<DiffSection>()
    val lines = diff.replace("\r\n", "\n").split('\n')
    var currentPath = ""
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            sections += DiffSection(currentPath, buffer.toString().trimEnd() + "\n")
            buffer.clear()
        }
    }
    for (line in lines) {
        when {
            line.startsWith("diff --git ") -> {
                flush()
                currentPath = parseGitDiffPath(line)
                buffer.append(line).append('\n')
            }
            line.startsWith("+++ b/") -> {
                val path = line.removePrefix("+++ b/").trim()
                if (path.isNotEmpty() && path != "/dev/null") currentPath = path
                buffer.append(line).append('\n')
            }
            line.startsWith("--- a/") -> {
                buffer.append(line).append('\n')
            }
            else -> buffer.append(line).append('\n')
        }
    }
    flush()
    return sections
}

private fun parseGitDiffPath(line: String): String {
    // diff --git a/path b/path
    val marker = " b/"
    val idx = line.lastIndexOf(marker)
    if (idx >= 0) return line.substring(idx + marker.length).trim()
    return line.substringAfter(" a/").substringBefore(' ').trim()
}

private fun countDiffLines(diff: String): Pair<Int, Int> {
    var added = 0
    var removed = 0
    for (line in diff.lineSequence()) {
        when {
            line.startsWith("+++") || line.startsWith("---") -> Unit
            line.startsWith("+") -> added++
            line.startsWith("-") -> removed++
        }
    }
    return added to removed
}

private fun changesForActivity(messageId: String, activity: ToolActivity): List<ReviewChange> {
    val preview = activity.preview ?: return changesFromArguments(messageId, activity)
    val diff = previewDiff(preview)
    if (diff.isBlank()) return emptyList()
    val fallbackPath = preview.path.ifBlank { sanitizeDiffPath(activity.title) }
    val sections = splitUnifiedDiffByFile(diff).ifEmpty {
        listOf(DiffSection(fallbackPath, diff))
    }
    return sections.mapIndexed { index, section ->
        val path = section.path.ifBlank { fallbackPath }
        val counts = countDiffLines(section.diff)
        ReviewChange(
            id = "$messageId:${activity.id}:$index",
            path = path,
            unifiedDiff = section.diff,
            added = counts.first,
            removed = counts.second,
        )
    }
}

/**
 * Desktop `previewDiff`: prefer the server diff, else rebuild one from the preview's plain text.
 * Checkpoints and several tools ship a preview whose `unifiedDiff` is empty while `oldText` /
 * `newText` are populated — without this the review list stays empty on those turns.
 */
private fun previewDiff(preview: ToolPreviewPayload): String {
    if (preview.unifiedDiff.isNotBlank()) return preview.unifiedDiff
    if (preview.oldText == null && preview.newText == null) return ""
    return syntheticDiff(preview.path, preview.oldText.orEmpty(), preview.newText.orEmpty())
}

/** Desktop `changesFromArguments`: rebuild file entries from the tool call's own arguments. */
private fun changesFromArguments(messageId: String, activity: ToolActivity): List<ReviewChange> {
    val args = activity.arguments
    val path = args.stringArg("path")
    return when {
        activity.toolName == "apply_patch" ->
            changesFromPatchInput(messageId, activity.id, args.stringArg("input"))
        activity.toolName == "replace_in_file" && path.isNotBlank() -> entryFromPlainEdit(
            messageId = messageId,
            activityId = activity.id,
            path = path,
            oldValue = args.stringArg("old_string"),
            newValue = args.stringArg("new_string"),
        )
        activity.toolName == "replace_many_in_file" && path.isNotBlank() -> {
            val edits = args?.get("edits") as? JsonArray ?: return emptyList()
            edits.flatMapIndexed { index, value ->
                val edit = value as? JsonObject
                entryFromPlainEdit(
                    messageId = messageId,
                    activityId = "${activity.id}:$index",
                    path = path,
                    oldValue = edit.stringArg("old_string"),
                    newValue = edit.stringArg("new_string"),
                )
            }
        }
        activity.toolName == "write_file" && path.isNotBlank() ->
            entryFromPlainEdit(messageId, activity.id, path, null, args.stringArg("content"))
        else -> emptyList()
    }
}

/** Desktop `changesFromPatchInput`: split a V4A patch body into one entry per file. */
private fun changesFromPatchInput(
    messageId: String,
    activityId: String,
    input: String,
): List<ReviewChange> {
    if (input.isBlank()) return emptyList()
    val entries = mutableListOf<ReviewChange>()
    var path = ""
    var body = mutableListOf<String>()
    fun flush() {
        if (path.isBlank()) return
        val changedLines = body.filter { line ->
            line.startsWith("@@") || line.startsWith("+") || line.startsWith("-") ||
                line.startsWith(" ")
        }
        val diff = (listOf("--- a/$path", "+++ b/$path") + changedLines).joinToString("\n")
        val counts = countDiffLines(diff)
        entries += ReviewChange(
            id = "$messageId:$activityId:${entries.size}",
            path = path,
            unifiedDiff = diff,
            added = counts.first,
            removed = counts.second,
        )
    }
    for (line in input.replace("\r\n", "\n").split('\n')) {
        val header = PATCH_FILE_HEADER.matchEntire(line)
        when {
            header != null -> {
                flush()
                path = header.groupValues[1].trim()
                body = mutableListOf()
            }
            line == "*** End Patch" -> {
                flush()
                path = ""
                body = mutableListOf()
            }
            path.isNotBlank() -> body += line
        }
    }
    if (path.isNotBlank()) flush()
    return entries
}

/** Desktop `fallbackEntry`: a single hunk rebuilt from a plain old/new pair. */
private fun entryFromPlainEdit(
    messageId: String,
    activityId: String,
    path: String,
    oldValue: String?,
    newValue: String?,
): List<ReviewChange> {
    val oldText = oldValue.orEmpty()
    val newText = newValue.orEmpty()
    if (oldText.isEmpty() && newText.isEmpty()) return emptyList()
    val diff = syntheticDiff(path, oldText, newText)
    val counts = countDiffLines(diff)
    return listOf(
        ReviewChange(
            id = "$messageId:$activityId",
            path = path,
            unifiedDiff = diff,
            added = counts.first,
            removed = counts.second,
        ),
    )
}

private fun syntheticDiff(path: String, oldText: String, newText: String): String = buildString {
    appendLine("--- a/$path")
    appendLine("+++ b/$path")
    appendLine("@@ -1 +1 @@")
    toLines(oldText).forEach { appendLine("-$it") }
    toLines(newText).forEach { appendLine("+$it") }
}.trimEnd()

private val PATCH_FILE_HEADER = Regex("^\\*\\*\\* (?:Add|Update|Delete) File:\\s*(.+)$")

/** Desktop `toLines`. */
private fun toLines(value: String?): List<String> {
    if (value.isNullOrEmpty()) return emptyList()
    val lines = value.replace("\r\n", "\n").split('\n').toMutableList()
    if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
    return lines
}

/** Desktop `sanitizeDiffPath`. */
private fun sanitizeDiffPath(path: String): String = path.trim()
    .replace(Regex("^(?:Write|Edit|Read|Delete|Move)\\s+", RegexOption.IGNORE_CASE), "")
    .replace(Regex("^[ab]/"), "")
    .replace(Regex("^/+"), "")

private fun JsonObject?.stringArg(key: String): String =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DiffReviewSheet(
    messages: List<ChatMessage>,
    workspaceId: String?,
    loadUncommitted: suspend (String?) -> Result<String>,
    onOpenDiff: (DiffViewRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scope by remember { mutableStateOf(DiffScope.Round) }
    var uncommitted by remember { mutableStateOf<List<ReviewChange>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptics = rememberAnyaHaptics()
    val loadFailedMessage = stringResource(R.string.chat_review_load_failed)

    LaunchedEffect(scope, workspaceId) {
        if (scope != DiffScope.Uncommitted) return@LaunchedEffect
        loading = true
        error = null
        val result = loadUncommitted(workspaceId)
        loading = false
        result.fold(
            onSuccess = { uncommitted = codeChangesFromUnifiedDiff(it) },
            onFailure = {
                uncommitted = emptyList()
                error = it.message ?: loadFailedMessage
            },
        )
    }

    val changes = remember(scope, messages, uncommitted) {
        when (scope) {
            DiffScope.Round -> extractReviewChanges(lastRoundMessages(messages))
            DiffScope.Session -> extractReviewChanges(messages)
            DiffScope.Uncommitted -> uncommitted
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_review_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.chat_diff_close))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
                DiffScopeChip(stringResource(R.string.chat_review_scope_round), scope == DiffScope.Round) {
                    haptics.tick()
                    scope = DiffScope.Round
                }
                DiffScopeChip(stringResource(R.string.chat_review_scope_uncommitted), scope == DiffScope.Uncommitted) {
                    haptics.tick()
                    scope = DiffScope.Uncommitted
                }
                DiffScopeChip(stringResource(R.string.chat_review_scope_session), scope == DiffScope.Session) {
                    haptics.tick()
                    scope = DiffScope.Session
                }
            }
            when {
                loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AnyaSpace.Xl),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
                error != null -> {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnyaColors.Danger,
                    )
                }
                changes.isEmpty() -> {
                    Text(
                        text = when (scope) {
                            DiffScope.Uncommitted -> stringResource(R.string.chat_review_empty_uncommitted)
                            DiffScope.Round -> stringResource(R.string.chat_review_empty_round)
                            DiffScope.Session -> stringResource(R.string.chat_review_empty_session)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        items(changes, key = { it.id }) { change ->
                            ReviewChangeRow(
                                change = change,
                                onClick = {
                                    haptics.tick()
                                    onOpenDiff(
                                        DiffViewRequest(
                                            path = change.path,
                                            unifiedDiff = change.unifiedDiff,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            AnyaSheetCard {
                AnyaSheetActionRow(
                    label = stringResource(R.string.chat_diff_close),
                    emphasized = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun DiffScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.onBackground,
            selectedLabelColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun ReviewChangeRow(change: ReviewChange, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = change.path.substringAfterLast('/').substringAfterLast('\\'),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = change.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (change.added > 0) {
                Text(
                    text = "+${change.added}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AnyaColors.Success,
                )
            }
            if (change.removed > 0) {
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "-${change.removed}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AnyaColors.Danger,
                )
            }
        }
    }
}
