package ai.anya.companion.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.model.session.ToolActivity
import kotlinx.coroutines.launch

internal data class ReadCodeSnapshot(
    val path: String,
    val firstLine: Int,
    val lastLine: Int,
    val content: String,
)

/** Extract only the successful, consecutively numbered text window recorded by the desktop. */
internal fun readCodeSnapshot(activity: ToolActivity): ReadCodeSnapshot? {
    if (activity.toolName !in setOf("read", "read_file") || activity.status == "running" || !activity.success) return null
    val result = activity.result.orEmpty()
    val path = Regex("^<path>([^\\n]+)</path>").find(result)?.groupValues?.get(1)
        ?: activity.arguments?.get("file_path")?.toString()?.trim('"')
        ?: activity.arguments?.get("path")?.toString()?.trim('"')
        ?: return null
    if (Regex("\\.(docx?|xlsx?|pptx?|pdf|png|jpe?g|webp|gif)$", RegexOption.IGNORE_CASE).containsMatchIn(path)) return null
    val body = if ("<content>\n" in result) result.substringAfter("<content>\n").substringBefore("\n</content>") else result
    val lines = body.lineSequence().mapNotNull { line ->
        Regex("^\\s*(\\d+)(?:: |\\|)(.*)$").find(line)?.let {
            it.groupValues[1].toIntOrNull()?.let { number -> number to it.groupValues[2] }
        }
    }.toList()
    if (lines.isEmpty() || lines.zipWithNext().any { (a, b) -> b.first != a.first + 1 }) return null
    return ReadCodeSnapshot(path, lines.first().first, lines.last().first, lines.joinToString("\n") { it.second })
}

@Composable
internal fun ReadCodeCard(activity: ToolActivity, loadCurrent: suspend (String) -> Result<String>) {
    val snapshot = remember(activity) { readCodeSnapshot(activity) } ?: return
    val scope = rememberCoroutineScope()
    var opened by remember(activity.id) { mutableStateOf(false) }
    var content by remember(activity.id) { mutableStateOf(snapshot.content) }
    var caption by remember(activity.id) { mutableStateOf("读取时快照") }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, bottom = 6.dp).clickable {
            opened = true
            content = snapshot.content
            caption = "读取时快照"
            scope.launch {
                loadCurrent(activity.id).onSuccess { current ->
                    val window = current.lines().drop(snapshot.firstLine - 1).take(snapshot.lastLine - snapshot.firstLine + 1)
                    if (window.size == snapshot.content.lines().size && window.zip(snapshot.content.lines()).all { (now, then) ->
                            now == then || ("…[" in then && now.startsWith(then.substringBefore("…[")))
                        }) {
                        content = current
                        caption = "当前文件 · 第 ${snapshot.firstLine}–${snapshot.lastLine} 行"
                    } else caption = "读取时快照 · 当前文件已变化"
                }.onFailure { caption = "读取时快照 · 无法读取当前文件" }
            }
        },
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("代码读取 · ${snapshot.path}", style = MaterialTheme.typography.labelMedium)
            Text("第 ${snapshot.firstLine}–${snapshot.lastLine} 行 · 点击查看", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (opened) AlertDialog(
        onDismissRequest = { opened = false },
        title = { Text(snapshot.path, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                Text(caption, style = MaterialTheme.typography.labelSmall)
                Text(
                    content,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = { TextButton(onClick = { opened = false }) { Text("关闭") } },
    )
}
