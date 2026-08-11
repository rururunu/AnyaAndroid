package ai.anya.companion.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed class ChatContentSegment {
    data class Markdown(val text: String) : ChatContentSegment()
    data class Chart(val specJson: String) : ChatContentSegment()
}

private val CHART_TYPES = setOf(
    "bar", "line", "scatter", "pie", "funnel", "gauge", "radar", "heatmap",
    "candlestick", "treemap", "sankey", "graph", "parallel",
    "bar3d", "scatter3d", "surface", "line3d", "custom",
)

/** Any fenced code block; language captured for chart routing. */
private val CODE_FENCE_RE = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")

internal fun tryParseChartSpec(text: String): String? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{")) return null
    return runCatching {
        val element = Json.parseToJsonElement(trimmed)
        val obj = element as? JsonObject ?: return null
        val type = obj["type"]?.jsonPrimitive?.content?.lowercase() ?: return null
        if (type !in CHART_TYPES) return null
        trimmed
    }.getOrNull()
}

/**
 * Splits assistant output into markdown and chart segments. Mirrors desktop:
 * only fenced ```chart (or ```json with a valid chart spec) becomes a chart;
 * all other text — including other code fences — stays intact as markdown.
 */
internal fun splitChatContent(content: String): List<ChatContentSegment> {
    if (content.isBlank()) return emptyList()
    val segments = mutableListOf<ChatContentSegment>()
    var cursor = 0
    CODE_FENCE_RE.findAll(content).forEach { fence ->
        val lang = fence.groupValues[1].trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
        val body = fence.groupValues[2]
        val chartSpec = if (lang == "chart" || lang == "json") tryParseChartSpec(body) else null
        if (chartSpec != null) {
            if (fence.range.first > cursor) {
                segments += ChatContentSegment.Markdown(content.substring(cursor, fence.range.first))
            }
            segments += ChatContentSegment.Chart(chartSpec)
            cursor = fence.range.last + 1
        }
        // Non-chart fences stay inside the surrounding markdown run untouched.
    }
    if (cursor < content.length) {
        segments += ChatContentSegment.Markdown(content.substring(cursor))
    }
    return segments.filter {
        when (it) {
            is ChatContentSegment.Markdown -> it.text.isNotBlank()
            is ChatContentSegment.Chart -> it.specJson.isNotBlank()
        }
    }
}

/**
 * Renders assistant message body with markdown, code fences, and chart blocks.
 */
@Composable
public fun AnyaChatContent(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    compact: Boolean = false,
) {
    val segments = remember(content) { splitChatContent(content) }
    if (segments.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ChatContentSegment.Markdown -> {
                    AnyaMarkdown(
                        content = segment.text.trim(),
                        textStyle = textStyle,
                        compact = compact,
                    )
                }
                is ChatContentSegment.Chart -> {
                    AnyaChartBlock(specJson = segment.specJson)
                }
            }
        }
    }
}
