package ai.anya.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.MessageStatus
import ai.anya.companion.core.model.session.ToolActivity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/* Port of desktop subagentPanel.ts: a sub-agent "session" is derived purely from the
 * parent tool activity and its children, so the phone needs no extra gateway RPC. */

internal data class SubagentEntry(
    val entryId: String,
    val parentActivityId: String,
    val taskIndex: Int,
    val title: String,
    val task: String,
    /** running | done | error */
    val status: String,
    val model: String?,
    val children: List<ToolActivity>,
)

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun taskLabels(activity: ToolActivity): List<String> {
    val args = activity.arguments ?: return activity.detail?.trim()?.takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty()
    (args["tasks"] as? JsonArray)?.let { tasks ->
        return tasks.mapNotNull { (it as? JsonObject)?.str("prompt") }
    }
    for (key in listOf("description", "task", "prompt")) {
        args.str(key)?.let { return listOf(it) }
    }
    return activity.detail?.trim()?.takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty()
}

private fun taskModels(activity: ToolActivity): List<String?> {
    val args = activity.arguments ?: return emptyList()
    (args["tasks"] as? JsonArray)?.let { tasks -> return tasks.map { (it as? JsonObject)?.str("model") } }
    return listOfNotNull(args.str("model"))
}

private fun childGroups(parentId: String, pool: List<ToolActivity>): List<List<ToolActivity>> {
    val groups = LinkedHashMap<String, MutableList<ToolActivity>>()
    for (a in pool) {
        if (a.parentActivityId != parentId) continue
        groups.getOrPut(a.subagentId ?: "default") { mutableListOf() } += a
    }
    return groups.values.toList()
}

private fun aggregateStatus(parentStatus: String, children: List<ToolActivity>): String = when {
    children.any { it.status == "error" } -> "error"
    children.any { it.status == "running" } || parentStatus == "running" -> "running"
    parentStatus == "error" -> "error"
    else -> "done"
}

private fun shortTaskTitle(prompt: String, index: Int): String {
    val lines = prompt.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val heading = lines.firstOrNull { Regex("^#{1,6}\\s+").containsMatchIn(it) }
    val source = heading ?: lines.firstOrNull().orEmpty()
    val cleaned = source
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace(Regex("^(?:任务|task|assignment)\\s*[:：-]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[`*_~]"), "")
        .trim()
    val prefix = "子代理 ${index + 1}"
    val title = if (cleaned.isNotEmpty()) "$prefix · $cleaned" else prefix
    return if (title.length > 72) title.take(71) + "..." else title
}

private fun completionFor(parent: ToolActivity, taskIndex: Int): String {
    val result = parent.result?.trim().orEmpty()
    if (parent.toolName != "run_parallel_subagents" || result.isEmpty()) return result
    val sections = result.split(Regex("^### Task \\d+\\s*$", RegexOption.MULTILINE))
    return sections.getOrNull(taskIndex + 1)?.trim().orEmpty()
}

internal fun subagentEntriesFor(parent: ToolActivity, pool: List<ToolActivity>, offset: Int = 0): List<SubagentEntry> {
    val labels = taskLabels(parent)
    val groups = childGroups(parent.id, pool)
    val models = taskModels(parent)
    val count = maxOf(1, labels.size, groups.size)
    return List(count) { index ->
        val children = groups.getOrElse(index) { emptyList() }
        val task = labels.getOrNull(index) ?: labels.firstOrNull() ?: parent.title.ifBlank { "执行任务" }
        SubagentEntry(
            entryId = "${parent.id}:$index",
            parentActivityId = parent.id,
            taskIndex = index,
            title = shortTaskTitle(task, offset + index),
            task = task,
            status = aggregateStatus(parent.status, children),
            model = models.getOrNull(index) ?: models.firstOrNull(),
            children = children,
        )
    }
}

/** Synthetic assistant message scoped to one sub-agent, rendered with the normal timeline. */
internal fun buildSubagentViewMessage(entry: SubagentEntry, parent: ToolActivity, pool: List<ToolActivity>): ChatMessage {
    val childIds = entry.children.mapTo(HashSet()) { it.id }
    val scoped = pool
        .filter { a -> a.id in childIds || entry.children.any { child -> a.parentActivityId == child.id } }
        .map { a -> if (a.id in childIds) a.copy(parentActivityId = null) else a }
    return ChatMessage(
        id = "subagent-${entry.entryId}",
        sessionId = "subagent",
        role = ChatRole.Assistant,
        content = if (entry.status == "running") "" else completionFor(parent, entry.taskIndex),
        toolActivities = scoped,
        status = when (entry.status) {
            "running" -> MessageStatus.Streaming
            "error" -> MessageStatus.Error
            else -> MessageStatus.Complete
        },
        createdAtEpochMs = System.currentTimeMillis(),
    )
}

/** Bottom sheet mirroring the desktop sub-agent panel: task, status, then its own work timeline. */
@Composable
internal fun SubagentSheet(
    parent: ToolActivity,
    pool: List<ToolActivity>,
    onOpenDiff: (DiffViewRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = remember(parent, pool) { subagentEntriesFor(parent, pool) }
    var selected by remember(parent.id) { mutableIntStateOf(0) }
    val entry = entries.getOrNull(selected) ?: entries.firstOrNull() ?: return
    val message = remember(entry, parent, pool) { buildSubagentViewMessage(entry, parent, pool) }
    var taskExpanded by remember(entry.entryId) { mutableStateOf(false) }
    var foldExpanded by remember(entry.entryId) { mutableStateOf(true) }

    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            Text(
                text = if (entries.size > 1) "子代理（${entries.size}）" else entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entries.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AnyaSpace.Sm),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entries.forEachIndexed { index, item ->
                        val active = index == selected
                        Text(
                            text = "任务 ${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { selected = index }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                        if (item.status == "running") {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp).align(Alignment.CenterVertically), strokeWidth = 1.5.dp)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val (label, color) = when (entry.status) {
                    "running" -> "运行中" to MaterialTheme.colorScheme.primary
                    "error" -> "失败" to MaterialTheme.colorScheme.error
                    else -> "已完成" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                if (entry.status == "running") CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
                entry.model?.let { Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${entry.children.size} 个操作",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AnyaSpace.Md)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { taskExpanded = !taskExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (taskExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "任务",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!taskExpanded) {
                        Text(
                            text = entry.task.lineSequence().firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (taskExpanded) {
                    Text(
                        text = entry.task,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                }
                if (message.toolActivities.isEmpty() && message.content.isBlank()) {
                    Text(
                        text = if (entry.status == "running") "子代理正在工作…" else "没有记录到子代理的操作",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AgentWorkDetails(
                        message = message,
                        foldExpanded = foldExpanded,
                        onFoldExpandedChange = { foldExpanded = it },
                        onOpenDiff = onOpenDiff,
                    )
                }
            }
        }
    }
}
