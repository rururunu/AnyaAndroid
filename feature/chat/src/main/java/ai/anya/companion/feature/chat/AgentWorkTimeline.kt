package ai.anya.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anya.companion.core.designsystem.component.AnyaChatContent
import ai.anya.companion.core.designsystem.component.AnyaMarkdown
import ai.anya.companion.core.designsystem.component.MascotFace
import ai.anya.companion.core.designsystem.component.MascotState
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.MessageCacheUsage
import ai.anya.companion.core.model.session.MessageStatus
import ai.anya.companion.core.model.session.PlanTaskItem
import ai.anya.companion.core.model.session.SessionCheckpoint
import ai.anya.companion.core.model.session.ToolActivity
import ai.anya.companion.core.model.session.WorkTimelineItem
import ai.anya.companion.core.model.session.estimateTokens
import ai.anya.companion.core.model.session.formatTokenCount
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/* ---------------------------------------------------------------------------------------------
 * Segment model — a port of desktop AgentWorkDetails.vue so both clients show the same turn
 * structure: interleaved reasoning / tool / narration / reply, with the completed preamble folded.
 * ------------------------------------------------------------------------------------------- */

internal sealed class WorkSegment {
    abstract val id: String

    data class Reasoning(override val id: String, val content: String) : WorkSegment()
    data class Narration(override val id: String, val content: String) : WorkSegment()
    data class Content(override val id: String, val content: String) : WorkSegment()
    data class Inline(
        override val id: String,
        val activities: MutableList<ToolActivity>,
        val operations: Boolean,
    ) : WorkSegment()
    data class Process(
        override val id: String,
        val activities: MutableList<ToolActivity>,
        val operations: Boolean,
    ) : WorkSegment()
}

private val SHOWCASE_KINDS = setOf("shell", "create", "edit", "delete", "move", "image")
private val TASK_LIST_TOOLS = setOf("update_tasks", "todo_write")
internal val SUBAGENT_TOOLS = setOf(
    "run_subagent", "run_parallel_subagents", "run_skill", "explore_codebase", "research_topic",
    "review_code", "review_security", "generate_word", "docx", "pandoc",
)
private val HIDE_RESULT_TOOLS = setOf(
    "read_file", "list_folder", "find_files", "search_files", "list_symbols", "fetch_url",
)
private val FILE_TOOLS = setOf(
    "read_file", "list_folder", "find_files", "read_chat", "read_shell_output", "get_context", "get_workspace",
)
private val SEARCH_TOOLS = setOf(
    "search_files", "grep", "list_symbols", "fetch_url", "search_memory", "search_chats",
)

internal fun visibleActivities(message: ChatMessage): List<ToolActivity> =
    message.toolActivities.filter { !(it.toolName == "ask_user" && it.status != "running") }

/** Task lists + finished images + shell/file edits stay in the open stream. */
private fun isInlineActivity(activity: ToolActivity): Boolean {
    if (activity.kind == "image") return activity.status != "running"
    if (activity.toolName in TASK_LIST_TOOLS) return true
    return activity.kind in SHOWCASE_KINDS
}

private fun isOperationsActivity(activity: ToolActivity): Boolean =
    activity.kind in SHOWCASE_KINDS && activity.kind != "shell" && activity.kind != "image"

private fun canMergeActivities(last: ToolActivity, next: ToolActivity): Boolean {
    // Keep image cards in their own segment so the completed fold can leave them visible.
    if (last.kind == "image" || next.kind == "image") return last.kind == "image" && next.kind == "image"
    return isOperationsActivity(last) == isOperationsActivity(next)
}

private fun pushActivity(segments: MutableList<WorkSegment>, activity: ToolActivity) {
    val inline = isInlineActivity(activity)
    val operations = isOperationsActivity(activity)
    val last = segments.lastOrNull()
    val lastActivities = when (last) {
        is WorkSegment.Inline -> if (inline && last.operations == operations) last.activities else null
        is WorkSegment.Process -> if (!inline && last.operations == operations) last.activities else null
        else -> null
    }
    if (lastActivities != null && lastActivities.isNotEmpty() && canMergeActivities(lastActivities.first(), activity)) {
        if (lastActivities.none { it.id == activity.id }) lastActivities += activity
        return
    }
    val id = (if (inline) "inline-" else "process-") + activity.id
    segments += if (inline) {
        WorkSegment.Inline(id, mutableListOf(activity), operations)
    } else {
        WorkSegment.Process(id, mutableListOf(activity), operations)
    }
}

/** Same parallel batch can be recorded under new activity ids on stream retry. */
private fun parallelSubagentFingerprint(activity: ToolActivity): String? {
    if (activity.toolName != "run_parallel_subagents") return null
    val tasks = activity.arguments?.get("tasks") as? JsonArray ?: return activity.id
    val prompts = tasks.map { value ->
        (value as? kotlinx.serialization.json.JsonObject)?.get("prompt")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }
    return activity.toolName + ":" + prompts.joinToString("\u0000")
}

private fun considerActivity(segments: MutableList<WorkSegment>, seen: MutableSet<String>, activity: ToolActivity) {
    if (!seen.add(activity.id)) return
    val fingerprint = parallelSubagentFingerprint(activity)
    if (fingerprint != null) {
        for (segment in segments) {
            val list = when (segment) {
                is WorkSegment.Inline -> segment.activities
                is WorkSegment.Process -> segment.activities
                else -> continue
            }
            val index = list.indexOfFirst { parallelSubagentFingerprint(it) == fingerprint }
            if (index >= 0) {
                list[index] = activity
                return
            }
        }
    }
    pushActivity(segments, activity)
}

private fun textOf(segment: WorkSegment, reasoning: Boolean): String? = when {
    reasoning && segment is WorkSegment.Reasoning -> segment.content
    !reasoning && segment is WorkSegment.Content -> segment.content
    else -> null
}

/**
 * Append any part of [finalText] not already covered by the matching segments — keeps the
 * reply visible even when the timeline is missing, partial or stale.
 */
private fun reconcileTrailingText(out: MutableList<WorkSegment>, reasoning: Boolean, finalText: String?) {
    val finalValue = finalText.orEmpty()
    if (finalValue.isEmpty()) return
    val accumulated = buildString { out.forEach { s -> textOf(s, reasoning)?.let(::append) } }
    if (finalValue == accumulated) return
    val kind = if (reasoning) "reasoning" else "content"
    if (accumulated.isEmpty() || finalValue.startsWith(accumulated)) {
        val missing = finalValue.substring(accumulated.length)
        if (missing.isEmpty()) return
        val last = out.lastOrNull()
        when {
            reasoning && last is WorkSegment.Reasoning -> out[out.lastIndex] = last.copy(content = last.content + missing)
            !reasoning && last is WorkSegment.Content -> out[out.lastIndex] = last.copy(content = last.content + missing)
            reasoning -> out += WorkSegment.Reasoning("$kind-final-${out.size}", missing)
            else -> out += WorkSegment.Content("$kind-final-${out.size}", missing)
        }
        return
    }
    if (accumulated.contains(finalValue)) return
    val notice = finalValue.trim()
    if (notice.isEmpty() || accumulated.contains(notice)) return
    val text = if (notice.startsWith("已停止") || notice.startsWith("Stopped")) "\n\n$notice" else notice
    out += if (reasoning) WorkSegment.Reasoning("$kind-final-${out.size}", text) else WorkSegment.Content("$kind-final-${out.size}", text)
}

private fun pickFullText(fromMessage: String, fromSegments: String): String = when {
    fromMessage.isEmpty() -> fromSegments
    fromSegments.isEmpty() -> fromMessage
    fromMessage == fromSegments -> fromMessage
    fromMessage.startsWith(fromSegments) -> fromMessage
    fromSegments.startsWith(fromMessage) -> fromSegments
    else -> if (fromSegments.length >= fromMessage.length) fromSegments else fromMessage
}

/** Once the turn is finished, fold every interleaved reasoning chunk into a single entry. */
private fun coalesceCompletedReasoning(out: List<WorkSegment>, message: ChatMessage): List<WorkSegment> {
    val fromMessage = message.reasoning?.trim().orEmpty()
    val fromSegments = out.filterIsInstance<WorkSegment.Reasoning>().joinToString("") { it.content }
    val combined = pickFullText(fromMessage, fromSegments)
    val withoutReasoning = out.filter { it !is WorkSegment.Reasoning }
    if (combined.isEmpty()) return withoutReasoning
    val block = WorkSegment.Reasoning("${message.id}-reasoning-completed", combined)
    val firstIdx = out.indexOfFirst { it is WorkSegment.Reasoning }
    if (firstIdx <= 0) return listOf(block) + withoutReasoning
    val before = out.subList(0, firstIdx).filter { it !is WorkSegment.Reasoning }
    val after = out.subList(firstIdx, out.size).filter { it !is WorkSegment.Reasoning }
    return before + block + after
}

/**
 * After tools finish, fold mid-turn narration (content before the last tool) into a collapsed
 * block. Only the final reply after the last tool stays outside the completed fold.
 */
private fun coalesceCompletedNarration(out: List<WorkSegment>, message: ChatMessage): List<WorkSegment> {
    val lastToolIdx = out.indexOfLast { it is WorkSegment.Process || it is WorkSegment.Inline }
    if (lastToolIdx < 0) return out
    val intermediate = mutableListOf<String>()
    val kept = mutableListOf<WorkSegment>()
    var insertAt = 0
    out.forEachIndexed { i, segment ->
        if (segment is WorkSegment.Content && i < lastToolIdx) {
            if (segment.content.isNotBlank()) intermediate += segment.content.trim()
            if (intermediate.size == 1) insertAt = kept.size
            return@forEachIndexed
        }
        kept += segment
    }
    val joined = intermediate.joinToString("\n\n").trim()
    val hasVisibleReply = kept.any { it is WorkSegment.Content }
    var visibleTail = ""
    var folded = joined
    if (!hasVisibleReply && intermediate.isNotEmpty()) {
        visibleTail = intermediate.last()
        folded = intermediate.dropLast(1).joinToString("\n\n").trim()
    }
    if (folded.isNotEmpty()) {
        if (kept.firstOrNull() is WorkSegment.Reasoning) insertAt = maxOf(insertAt, 1)
        kept.add(insertAt.coerceAtMost(kept.size), WorkSegment.Narration("${message.id}-narration-completed", folded))
    }
    if (visibleTail.isNotEmpty()) {
        kept += WorkSegment.Content("${message.id}-narration-tail", visibleTail)
    }
    return kept
}

/** Single chronological stream with process-detail chunks interleaved. */
internal fun buildWorkSegments(message: ChatMessage, streaming: Boolean): List<WorkSegment> {
    val visible = visibleActivities(message)
    val topLevel = visible.filter { it.parentActivityId == null }
    val byId = topLevel.associateBy { it.id }
    val out = mutableListOf<WorkSegment>()
    val seen = HashSet<String>()
    for (item in message.workTimeline) {
        when (item) {
            is WorkTimelineItem.Reasoning -> if (item.content.isNotBlank()) out += WorkSegment.Reasoning(item.id, item.content)
            is WorkTimelineItem.Content -> if (item.content.isNotBlank()) out += WorkSegment.Content(item.id, item.content)
            is WorkTimelineItem.Tool -> byId[item.toolActivityId]?.let { considerActivity(out, seen, it) }
        }
    }
    reconcileTrailingText(out, reasoning = true, message.reasoning)
    reconcileTrailingText(out, reasoning = false, message.content)
    for (activity in topLevel) considerActivity(out, seen, activity)
    if (streaming) return out
    return coalesceCompletedNarration(coalesceCompletedReasoning(out, message), message)
}

internal fun isGeneratedImageSegment(segment: WorkSegment): Boolean {
    val activities = when (segment) {
        is WorkSegment.Inline -> segment.activities
        is WorkSegment.Process -> segment.activities
        else -> return false
    }
    return activities.isNotEmpty() && activities.all { it.kind == "image" }
}

/** After the turn completes, images stay next to the reply instead of inside the fold. */
internal fun isReplySegment(segment: WorkSegment): Boolean =
    segment is WorkSegment.Content || isGeneratedImageSegment(segment)

/* ------------------------------ display helpers (toolActivityDisplay.ts) ------------------------------ */

internal fun shouldShowActivityResult(activity: ToolActivity): Boolean =
    !activity.result.isNullOrBlank() && activity.status != "running" && activity.toolName !in HIDE_RESULT_TOOLS

internal fun hasExpandableActivityContent(activity: ToolActivity, childCount: Int = 0): Boolean {
    if (activity.toolName in SUBAGENT_TOOLS) return childCount > 0
    if (!activity.detail.isNullOrBlank()) return true
    if (shouldShowActivityResult(activity)) return true
    return childCount > 0
}

private enum class ActivityCategory { File, Search, Other }

private fun categorizeActivity(activity: ToolActivity): ActivityCategory = when {
    activity.toolName in SEARCH_TOOLS -> ActivityCategory.Search
    activity.toolName in FILE_TOOLS -> ActivityCategory.File
    Regex("^(search|grep|find)\\b", RegexOption.IGNORE_CASE).containsMatchIn(activity.title) -> ActivityCategory.Search
    Regex("^read\\b", RegexOption.IGNORE_CASE).containsMatchIn(activity.title) -> ActivityCategory.File
    activity.kind == "read" -> ActivityCategory.File
    else -> ActivityCategory.Other
}

internal fun summarizeProcessActivities(activities: List<ToolActivity>): String {
    if (activities.isEmpty()) return "过程详情"
    if (activities.size == 1) {
        val a = activities.first()
        return a.title.trim().ifEmpty { a.toolName.trim() }.ifEmpty { "过程详情" }
    }
    var files = 0
    var searches = 0
    var others = 0
    for (a in activities) {
        when (categorizeActivity(a)) {
            ActivityCategory.File -> files++
            ActivityCategory.Search -> searches++
            ActivityCategory.Other -> others++
        }
    }
    val parts = mutableListOf<String>()
    if (files > 0) parts += "浏览 $files 个文件"
    if (searches > 0) parts += "$searches 次搜索"
    if (others > 0) parts += "$others 个步骤"
    return parts.joinToString("，")
}

/** Compact stats for the completed work fold header (tools · searches · thinking). */
internal fun summarizeWorkFoldMeta(activities: List<ToolActivity>, reasoningSegments: Int): String {
    val tools = activities.filter { it.toolName != "ask_user" }
    val searchCount = tools.count { categorizeActivity(it) == ActivityCategory.Search }
    val parts = mutableListOf<String>()
    if (tools.isNotEmpty()) parts += "${tools.size} 个工具"
    if (searchCount > 0) parts += "$searchCount 次搜索"
    if (reasoningSegments > 0) parts += "$reasoningSegments 段思考"
    return parts.joinToString(" · ")
}

internal fun countReasoningSegments(message: ChatMessage): Int {
    val fromTimeline = message.workTimeline.count { it is WorkTimelineItem.Reasoning && it.content.isNotBlank() }
    if (fromTimeline > 0) return fromTimeline
    return if (message.reasoning.isNullOrBlank()) 0 else 1
}

/** Short variant label for disclosure rows. */
internal fun toolVariantLabel(activity: ToolActivity): String = when {
    activity.kind == "shell" || activity.toolName == "run_command" -> "终端"
    activity.kind == "image" || activity.toolName == "generate_image" -> "图片"
    activity.toolName in SEARCH_TOOLS || activity.kind == "search" -> "搜索"
    activity.toolName in FILE_TOOLS || activity.kind == "read" -> "读取"
    activity.kind == "create" || activity.toolName == "write_file" -> "写入"
    activity.kind == "edit" || activity.kind == "delete" || activity.kind == "move" -> "编辑"
    activity.toolName in SUBAGENT_TOOLS -> "子代理"
    else -> "工具"
}

private fun argumentString(activity: ToolActivity, vararg keys: String): String {
    val args = activity.arguments ?: return ""
    for (key in keys) {
        val value = (args[key] as? JsonPrimitive)?.contentOrNull?.trim()
        if (!value.isNullOrEmpty()) return value
    }
    return ""
}

/** Ellipsized summary for collapsed tool rows. */
internal fun activitySummaryLine(activity: ToolActivity): String {
    val path = activity.preview?.path?.trim()?.takeIf { it.isNotEmpty() }
        ?: argumentString(activity, "path", "AbsolutePath", "TargetFile", "file_path")
    if (path.isNotEmpty()) return path.replace('\\', '/').substringAfterLast('/').ifEmpty { path }
    val query = argumentString(activity, "query", "Query", "pattern")
    if (query.isNotEmpty()) return query
    val command = argumentString(activity, "CommandLine", "command", "commandLine")
    if (command.isNotEmpty()) return command
    val title = activity.title.trim()
    if (title.isNotEmpty()) {
        val sep = title.indexOf(' ')
        return if (sep == -1) title else title.substring(sep + 1).trim().ifEmpty { title }
    }
    val detail = activity.detail?.trim()
    if (!detail.isNullOrEmpty()) {
        val line = detail.lineSequence().firstOrNull()?.trim().orEmpty()
        if (line.isNotEmpty()) return if (line.length > 120) line.take(117) + "..." else line
    }
    return activity.toolName.trim()
}

private fun errorSummaryLine(activity: ToolActivity): String {
    val detail = activity.detail?.trim()?.takeIf { it.isNotEmpty() } ?: activity.result?.trim()
    if (detail.isNullOrEmpty()) return "失败"
    val line = detail.lineSequence().firstOrNull()?.trim() ?: detail
    return if (line.length > 120) line.take(117) + "..." else line
}

internal fun isProcessSegmentCollapsible(activities: List<ToolActivity>, pool: List<ToolActivity>): Boolean {
    if (activities.size > 1) return true
    val activity = activities.firstOrNull() ?: return false
    if (activity.toolName in SUBAGENT_TOOLS) return false
    val childCount = pool.count { it.parentActivityId == activity.id }
    return hasExpandableActivityContent(activity, childCount)
}

/* ------------------------------------------ composables ------------------------------------------ */

/**
 * Screen-level hooks for per-turn chrome: rewind points, cache usage and the sub-agent
 * inspector. Provided once around the message list so deep cards can reach them.
 */
@Immutable
internal data class TurnActions(
    val checkpointsByUserMessage: Map<String, SessionCheckpoint> = emptyMap(),
    val cacheUsages: Map<String, MessageCacheUsage> = emptyMap(),
    val rewinding: Boolean = false,
    val onRewind: (userMessageId: String) -> Unit = {},
    val onInspectSubagent: (parent: ToolActivity, pool: List<ToolActivity>) -> Unit = { _, _ -> },
    val onReadCodePreview: suspend (String) -> Result<String> = { Result.failure(IllegalStateException("unavailable")) },
)

internal val LocalTurnActions = staticCompositionLocalOf { TurnActions() }

/**
 * Whether the model's reasoning is rendered in the transcript (Settings → Chat display).
 * Off by default on phones. Only [WorkSegment.Reasoning] honours it — mid-turn narration stays
 * visible because it is the agent talking to the user, not hidden chain-of-thought.
 */
internal val LocalShowThinking = staticCompositionLocalOf { false }

/** Per-message UI state for the process toggles (desktop `processOpen` / `userToggledProcess`). */
private class ProcessOpenState {
    val open = mutableStateMapOf<String, Boolean>()
    val userToggled = mutableStateMapOf<String, Boolean>()
}

/**
 * Renders an assistant turn like the desktop: live turns keep chronological interleaving;
 * completed turns fold thinking + tools under "已完成" and leave images + the reply open.
 */
@Composable
internal fun AgentWorkDetails(
    message: ChatMessage,
    foldExpanded: Boolean,
    onFoldExpandedChange: (Boolean) -> Unit,
    onOpenDiff: (DiffViewRequest) -> Unit,
    /** True when the desktop left this turn's execution process out of the history payload. */
    detailOmitted: Boolean = false,
    /** Called once, the first time the user expands something, to fetch that detail. */
    onNeedDetail: () -> Unit = {},
) {
    val streaming = message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending
    val segments = remember(message, streaming) { buildWorkSegments(message, streaming) }
    if (segments.isEmpty()) return
    val visible = remember(message.toolActivities) { visibleActivities(message) }
    val imageRefs = remember(message.toolActivities) { collectGeneratedImageRefs(message.toolActivities) }
    val processState = remember(message.id) { ProcessOpenState() }
    val waitingForAskUser = visible.any { it.toolName == "ask_user" && it.status == "running" }

    // A light payload ships only the final answer; the process is fetched the first time the user
    // opens it, instead of being pulled in for every turn on session open.
    val anyProcessOpen = remember(processState) {
        derivedStateOf { processState.open.values.any { it } }
    }
    LaunchedEffect(detailOmitted, anyProcessOpen.value) {
        if (detailOmitted && anyProcessOpen.value) onNeedDetail()
    }

    // Collapsed by default; only auto-expand while showcase work is actively running.
    val statusKey = segments.joinToString("|") { s ->
        if (s is WorkSegment.Process) s.id + ":" + s.activities.joinToString(",") { it.status } else s.id
    }
    LaunchedEffect(statusKey, waitingForAskUser, streaming) {
        for (segment in segments) {
            if (segment !is WorkSegment.Process) continue
            if (processState.userToggled[segment.id] == true) continue
            processState.open[segment.id] = waitingForAskUser ||
                segment.activities.any { it.status == "running" && it.kind in SHOWCASE_KINDS }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        if (streaming) {
            var seenReasoning = false
            segments.forEach { segment ->
                val showReasoningSummary = segment is WorkSegment.Reasoning && !seenReasoning
                if (segment is WorkSegment.Reasoning) seenReasoning = true
                WorkSegmentView(
                    segment = segment,
                    streaming = true,
                    showReasoningSummary = showReasoningSummary,
                    pool = visible,
                    processState = processState,
                    imageRefs = imageRefs,
                    onOpenDiff = onOpenDiff,
                )
            }
        } else {
            val preamble = segments.filterNot(::isReplySegment)
            val reply = segments.filter(::isReplySegment)
            if (preamble.isNotEmpty()) {
                val showThinking = LocalShowThinking.current
                val meta = remember(message, showThinking) {
                    summarizeWorkFoldMeta(
                        visible,
                        if (showThinking) countReasoningSegments(message) else 0,
                    )
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFoldExpandedChange(!foldExpanded) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (foldExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Text(
                            text = "已完成",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (meta.isNotEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        }
                    }
                    if (foldExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                        ) {
                            preamble.forEach { segment ->
                                WorkSegmentView(
                                    segment = segment,
                                    streaming = false,
                                    showReasoningSummary = false,
                                    pool = visible,
                                    processState = processState,
                                    imageRefs = imageRefs,
                                    onOpenDiff = onOpenDiff,
                                )
                            }
                        }
                    }
                }
            }
            reply.forEach { segment ->
                WorkSegmentView(
                    segment = segment,
                    streaming = false,
                    showReasoningSummary = true,
                    pool = visible,
                    processState = processState,
                    imageRefs = imageRefs,
                    onOpenDiff = onOpenDiff,
                    collapsibleProcess = false,
                )
            }
        }
    }
}

@Composable
private fun WorkSegmentView(
    segment: WorkSegment,
    streaming: Boolean,
    showReasoningSummary: Boolean,
    pool: List<ToolActivity>,
    processState: ProcessOpenState,
    imageRefs: Map<String, GeneratedImageRef>,
    onOpenDiff: (DiffViewRequest) -> Unit,
    collapsibleProcess: Boolean = true,
) {
    when (segment) {
        is WorkSegment.Reasoning -> ReasoningSection(
            reasoning = segment.content,
            streaming = streaming,
            showSummary = showReasoningSummary,
            visible = LocalShowThinking.current,
        )
        is WorkSegment.Narration -> ReasoningSection(
            reasoning = segment.content,
            streaming = false,
            showSummary = true,
            title = "执行详情",
        )
        is WorkSegment.Content -> AssistantMarkdown(content = segment.content, imageRefs = imageRefs)
        is WorkSegment.Inline -> ToolActivityList(activities = segment.activities, pool = pool, onOpenDiff = onOpenDiff)
        is WorkSegment.Process -> {
            val collapsible = collapsibleProcess && isProcessSegmentCollapsible(segment.activities, pool)
            if (!collapsible) {
                ToolActivityList(activities = segment.activities, pool = pool, onOpenDiff = onOpenDiff)
            } else {
                val open = processState.open[segment.id] == true
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                processState.userToggled[segment.id] = true
                                processState.open[segment.id] = !open
                            }
                            .padding(vertical = 3.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (open) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Text(
                            text = summarizeProcessActivities(segment.activities),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (segment.activities.any { it.status == "running" }) {
                            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                        }
                    }
                    if (open) {
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                ToolActivityList(activities = segment.activities, pool = pool, onOpenDiff = onOpenDiff)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reply markdown. Desktop writes generated pictures as `![alt](path:C:\…)`; those paths only
 * exist on the PC, so they become fetchable tiles here instead of broken images.
 *
 * When the same file already appears in a [GeneratedImageToolCard] (via [imageRefs]),
 * skip the duplicate tile and keep only the prose.
 */
@Composable
internal fun AssistantMarkdown(
    content: String,
    imageRefs: Map<String, GeneratedImageRef> = emptyMap(),
    compact: Boolean = false,
) {
    val parts = remember(content, imageRefs.keys) {
        splitLocalImages(content, skipFileNames = imageRefs.keys)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        parts.forEach { part ->
            when (part) {
                is ContentPart.Text -> AnyaChatContent(
                    content = part.text,
                    textStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    compact = compact,
                )
                is ContentPart.LocalImage -> {
                    val name = part.path.replace('\\', '/').substringAfterLast('/')
                    val ref = imageRefs[name]?.let { known ->
                        known.copy(revisedPrompt = part.alt ?: known.revisedPrompt)
                    } ?: GeneratedImageRef(path = part.path, revisedPrompt = part.alt)
                    GeneratedImageTile(image = ref)
                }
            }
        }
    }
}

internal sealed class ContentPart {
    data class Text(val text: String) : ContentPart()
    data class LocalImage(val path: String, val alt: String?) : ContentPart()
}

/**
 * Split reply text from desktop `path:` images.
 * [skipFileNames]: file names already rendered by the generate_image tool card.
 */
internal fun splitLocalImages(
    content: String,
    skipFileNames: Set<String> = emptySet(),
): List<ContentPart> {
    if (content.isBlank()) return emptyList()
    val parts = mutableListOf<ContentPart>()
    var cursor = 0
    MARKDOWN_LOCAL_IMAGE.findAll(content).forEach { match ->
        if (match.range.first > cursor) {
            val text = content.substring(cursor, match.range.first)
            if (text.isNotBlank()) parts += ContentPart.Text(text.trim())
        }
        val raw = match.groupValues[2].trim().removePrefix("path:")
        val name = raw.replace('\\', '/').substringAfterLast('/')
        if (name.isEmpty() || name !in skipFileNames) {
            parts += ContentPart.LocalImage(
                path = raw,
                alt = match.groupValues[1].trim().takeIf { it.isNotEmpty() },
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        val tail = content.substring(cursor)
        if (tail.isNotBlank()) parts += ContentPart.Text(tail.trim())
    }
    return parts
}

/** Process-row presentation for non-showcase tools: variant · summary, expandable detail / result. */
@Composable
internal fun ToolActivityRow(
    activity: ToolActivity,
    pool: List<ToolActivity> = emptyList(),
    onOpenDiff: (DiffViewRequest) -> Unit = {},
) {
    val running = activity.status == "running"
    val failed = activity.status == "error" || !activity.success
    val children = remember(activity.id, pool) { pool.filter { it.parentActivityId == activity.id } }
    val isSubagent = activity.toolName in SUBAGENT_TOOLS
    val expandable = remember(activity, children.size) { hasExpandableActivityContent(activity, children.size) }
    var expanded by rememberSaveable(activity.id) { mutableStateOf(false) }
    val summary = remember(activity) {
        if (failed) errorSummaryLine(activity) else activitySummaryLine(activity)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (expandable) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            } else {
                Icon(
                    imageVector = activityIcon(activity),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = toolVariantLabel(activity),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (running && activity.toolName == "ask_user") {
                Text(
                    text = "等待回答",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSubagent) {
                // Desktop "查看子代理": opens the scoped panel for this run.
                val actions = LocalTurnActions.current
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "查看子代理",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { actions.onInspectSubagent(activity, pool) }
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (expanded) {
            ReadCodeCard(activity, LocalTurnActions.current.onReadCodePreview)
            val detail = activity.detail?.trim()
            val body = if (!detail.isNullOrEmpty()) detail else activity.result?.trim().orEmpty()
            if (children.isNotEmpty()) {
                // Sub-agent work nests under its parent row, like the desktop subagent card.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, bottom = 4.dp),
                ) {
                    ToolActivityList(activities = children, pool = pool, onOpenDiff = onOpenDiff)
                }
            }
            if (body.isNotEmpty() && !isSubagent && readCodeSnapshot(activity) == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, bottom = 4.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (!detail.isNullOrEmpty()) {
                        AnyaMarkdown(
                            content = detail,
                            textStyle = MaterialTheme.typography.bodySmall,
                            compact = true,
                        )
                    } else {
                        Text(
                            text = if (body.length > 4000) body.take(4000) + "\n…" else body,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 17.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------ turn footer + activity line ------------------------------ */

/** Desktop `turnMascotState`: the face next to the turn stats follows what the agent is doing. */
internal fun turnMascotState(message: ChatMessage): MascotState = when (message.status) {
    MessageStatus.Error -> MascotState.Error
    MessageStatus.Cancelled -> MascotState.Idle
    MessageStatus.Complete -> MascotState.Done
    MessageStatus.Pending, MessageStatus.Streaming -> when {
        message.toolActivities.any { it.toolName == "ask_user" && it.status == "running" } -> MascotState.Waiting
        message.toolActivities.any { it.status == "running" } -> MascotState.Working
        message.content.isNotBlank() -> MascotState.Talking
        else -> MascotState.Thinking
    }
}

private fun mascotStateLabel(state: MascotState): String = when (state) {
    MascotState.Idle -> "待机"
    MascotState.Thinking -> "思考中"
    MascotState.Working -> "执行工具中"
    MascotState.Talking -> "回复中"
    MascotState.Waiting -> "等待你的输入"
    MascotState.Done -> "已完成"
    MascotState.Error -> "出错了"
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = maxOf(0L, ms / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$minutes m $seconds s" else "$seconds s"
}

/**
 * Stats row under an assistant turn, matching the desktop message actions: mascot + state
 * caption while live, "已处理 N s", "≈ N tokens" and the copy button.
 */
@Composable
internal fun TurnFooter(
    message: ChatMessage,
    turnStartedAtEpochMs: Long?,
    turnUserMessage: ChatMessage?,
    copyButton: @Composable () -> Unit,
) {
    val live = message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending
    val clock by produceState(initialValue = System.currentTimeMillis(), key1 = live) {
        while (live) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val duration: String? = turnStartedAtEpochMs?.takeIf { it > 0 }?.let { started ->
        val finished = if (live) clock else message.completedAtEpochMs
        if (finished == null || finished < started) null else formatDuration(finished - started)
    }
    val tokens = remember(message, turnUserMessage) {
        (turnUserMessage?.estimateTokens() ?: 0) + message.estimateTokens()
    }
    val state = turnMascotState(message)
    val caption = if (state == MascotState.Idle || state == MascotState.Done) "" else mascotStateLabel(state)
    val showFooter = duration != null || (!live && (tokens > 0 || message.content.isNotBlank()))
    if (!showFooter) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (duration != null) {
            MascotFace(
                modifier = Modifier.size(20.dp),
                state = state,
                followPointer = false,
                turnOnChange = true,
            )
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "已处理 $duration",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
        if (tokens > 0 && !live) {
            Text(
                text = "≈ ${formatTokenCount(tokens)} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
        val cacheHit = LocalTurnActions.current.cacheUsages[message.id]?.hitPercent
        if (cacheHit != null && !live) {
            Text(
                text = "缓存命中 $cacheHit%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (message.content.isNotBlank() && !live) copyButton()
    }
}

/** Desktop `activityLabel`: transient status lines that are not part of the work stream. */
internal fun activityLabel(message: ChatMessage, hasDuration: Boolean): String? {
    val status = message.activityStatus
    if (status == "context_compacting") return "正在压缩上下文…"
    if (status != null && status.startsWith("stream_retry")) {
        val parts = status.split(":")
        val attempt = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val max = parts.getOrNull(2)?.toIntOrNull() ?: 5
        return "连接中断，正在重试 ($attempt/$max)..."
    }
    if (status == "reject_empty_completion") return "检测到空完成，正在纠正并强制重试修改..."
    val live = message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending
    val waitingAsk = message.toolActivities.any { it.toolName == "ask_user" && it.status == "running" }
    if (!live || waitingAsk) return null
    if (status == "analyzing_images" && message.content.isBlank() && message.reasoning.isNullOrBlank()) {
        return "正在分析图片…"
    }
    val hasWorkStream = !message.reasoning.isNullOrBlank() || message.toolActivities.isNotEmpty()
    if (hasWorkStream) return null
    // The mascot in the stats row already narrates thinking / replying.
    if (hasDuration) return null
    return if (message.content.isNotBlank()) "回答中" else "处理中..."
}

@Composable
internal fun AssistantActivityIndicator(label: String, drawing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (drawing) {
            Icon(
                imageVector = Icons.Rounded.Brush,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ------------------------------ embedded task list (update_tasks / todo_write) ------------------------------ */

internal fun tasksFromActivity(activity: ToolActivity): List<PlanTaskItem> {
    if (activity.toolName !in TASK_LIST_TOOLS) return emptyList()
    val raw = activity.arguments?.get("tasks") as? JsonArray ?: return emptyList()
    return raw.mapNotNull { value ->
        val item = value as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val content = item["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (content.isEmpty()) return@mapNotNull null
        PlanTaskItem(
            content = content,
            status = item["status"]?.jsonPrimitive?.contentOrNull ?: "pending",
            level = item["level"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }
}

@Composable
internal fun EmbeddedTaskList(tasks: List<PlanTaskItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tasks.forEach { task ->
            val done = task.status == "completed" || task.status == "done"
            val active = task.status == "in_progress" || task.status == "running"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = (task.level.coerceIn(0, 4) * 12).dp),
            ) {
                Icon(
                    imageVector = when {
                        done -> Icons.Rounded.CheckCircle
                        active -> Icons.Rounded.RadioButtonChecked
                        else -> Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = task.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

private fun activityIcon(activity: ToolActivity): ImageVector = when {
    activity.toolName in SUBAGENT_TOOLS -> Icons.Rounded.Hub
    activity.kind == "image" -> Icons.Rounded.Brush
    activity.kind == "read" || activity.toolName in FILE_TOOLS -> Icons.Rounded.FolderOpen
    activity.toolName in SEARCH_TOOLS || activity.kind == "search" -> Icons.Rounded.Search
    else -> Icons.Rounded.Build
}
