package ai.anya.companion.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace

internal data class ImageAnalysisBlock(
    val model: String,
    val text: String,
)

internal data class ParsedUserExtras(
    val body: String,
    val selection: String? = null,
    val selectionLines: Int? = null,
    val imageAnalyses: List<ImageAnalysisBlock> = emptyList(),
)

private val SELECTION_OPEN = Regex("""\n\n<peek-selection lines="(\d+)">\n""")
private const val SELECTION_CLOSE = "\n</peek-selection>"
private val ANALYSIS_TAG = Regex(
    """<peek-image-analysis\s+model="([^"]*)">\s*([\s\S]*?)\s*</peek-image-analysis>""",
)
private val ENV_CONTEXT_FENCE = Regex(
    """```(?:environment|env-context)\s*\n([\s\S]*?)```""",
    RegexOption.IGNORE_CASE,
)

internal fun parseUserExtras(content: String): ParsedUserExtras {
    var clean = content
    var selection: String? = null
    var selectionLines: Int? = null
    val open = SELECTION_OPEN.find(clean)
    if (open != null) {
        val closeIndex = clean.lastIndexOf(SELECTION_CLOSE)
        if (closeIndex >= open.range.last + 1) {
            selection = clean.substring(open.range.last + 1, closeIndex)
            selectionLines = open.groupValues[1].toIntOrNull()
            clean = clean.substring(0, open.range.first).trimEnd()
        }
    }
    val analyses = mutableListOf<ImageAnalysisBlock>()
    clean = ANALYSIS_TAG.replace(clean) { match ->
        analyses += ImageAnalysisBlock(
            model = match.groupValues[1],
            text = match.groupValues[2].trim(),
        )
        ""
    }.trim()
    return ParsedUserExtras(
        body = clean,
        selection = selection,
        selectionLines = selectionLines,
        imageAnalyses = analyses,
    )
}

internal fun extractEnvironmentFence(content: String): String? =
    ENV_CONTEXT_FENCE.find(content)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

private const val SOFT_INJECT_FOLD_LINES = 4

internal fun shouldFoldSoftInject(text: String): Boolean =
    text.lineSequence().count() > SOFT_INJECT_FOLD_LINES || text.length > 280

@Composable
internal fun SoftInjectCard(content: String) {
    val text = stripSoftInjectMarker(content)
    val needsFold = remember(text) { shouldFoldSoftInject(text) }
    var expanded by remember(text) { mutableStateOf(false) }
    val haptics = rememberAnyaHaptics()
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
        ) {
            Text(
                text = "软注入",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text.ifBlank { "…" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (needsFold && !expanded) SOFT_INJECT_FOLD_LINES else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            if (needsFold) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        haptics.tick()
                        expanded = !expanded
                    },
                )
            }
        }
    }
}

@Composable
internal fun ImageAnalysisCard(block: ImageAnalysisBlock) {
    var expanded by remember(block.text) { mutableStateOf(false) }
    val haptics = rememberAnyaHaptics()
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                expanded = !expanded
            },
    ) {
        Column(
            modifier = Modifier.padding(AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
        ) {
            Text(
                text = if (block.model.isBlank()) "图片分析" else "图片分析 · ${block.model}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SelectionCard(selection: String, lineCount: Int?) {
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
        ) {
            Text(
                text = if (lineCount != null && lineCount > 0) "选区 · $lineCount 行" else "选区",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = selection,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EnvironmentFenceCard(body: String) {
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
        ) {
            Text(
                text = "环境上下文",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
