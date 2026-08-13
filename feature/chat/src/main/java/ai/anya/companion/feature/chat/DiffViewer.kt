package ai.anya.companion.feature.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace

/** A file diff the user asked to inspect from a chat card. */
public data class DiffViewRequest(
    public val path: String,
    public val unifiedDiff: String,
)

private data class ParsedDiff(
    val added: Int,
    val removed: Int,
    val truncated: Boolean,
    val visibleLines: List<String>,
)

private const val MAX_DIFF_LINES = 3_000

private fun isFileHeaderLine(line: String): Boolean =
    line.startsWith("diff --git") ||
        line.startsWith("index ") ||
        line.startsWith("--- ") ||
        line.startsWith("+++ ") ||
        line.startsWith("new file mode") ||
        line.startsWith("deleted file mode") ||
        line.startsWith("similarity index") ||
        line.startsWith("rename from") ||
        line.startsWith("rename to")

/** Strip git file headers and count +/- lines; long diffs get truncated for rendering. */
private fun parseUnifiedDiff(diff: String): ParsedDiff {
    var added = 0
    var removed = 0
    val visible = mutableListOf<String>()
    var truncated = false
    diff.lineSequence().forEach { raw ->
        if (isFileHeaderLine(raw)) return@forEach
        when {
            raw.startsWith("+") -> added++
            raw.startsWith("-") -> removed++
        }
        if (visible.size < MAX_DIFF_LINES) {
            visible += raw
        } else {
            truncated = true
        }
    }
    while (visible.isNotEmpty() && visible.last().isBlank()) {
        visible.removeAt(visible.lastIndex)
    }
    return ParsedDiff(added, removed, truncated, visible)
}

@Composable
private fun rememberDiffAnnotatedString(lines: List<String>): AnnotatedString {
    val addedBg = AnyaColors.Success.copy(alpha = 0.14f)
    val removedBg = AnyaColors.Danger.copy(alpha = 0.14f)
    val addedFg = AnyaColors.Success
    val removedFg = AnyaColors.Danger
    val hunkFg = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(lines) {
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("@@") -> withStyle(SpanStyle(color = hunkFg)) { append(line) }
                    line.startsWith("+") -> withStyle(
                        SpanStyle(color = addedFg, background = addedBg),
                    ) { append(line) }
                    line.startsWith("-") -> withStyle(
                        SpanStyle(color = removedFg, background = removedBg),
                    ) { append(line) }
                    else -> append(line)
                }
                if (index != lines.lastIndex) append('\n')
            }
        }
    }
}

/**
 * Full-screen unified diff viewer, colored like the desktop diff panel:
 * green added rows, red removed rows, muted hunk headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DiffViewerSheet(
    request: DiffViewRequest,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsed = remember(request.unifiedDiff) { parseUnifiedDiff(request.unifiedDiff) }
    val diffText = rememberDiffAnnotatedString(parsed.visibleLines)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.path.substringAfterLast('/').substringAfterLast('\\'),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = request.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (parsed.added > 0) {
                    Text(
                        text = "+${parsed.added}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AnyaColors.Success,
                    )
                }
                if (parsed.removed > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "-${parsed.removed}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AnyaColors.Danger,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(AnyaSpace.CardRadius),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(AnyaSpace.Md),
                ) {
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        softWrap = false,
                    )
                    if (parsed.truncated) {
                        Text(
                            text = "…diff 过长，仅显示前 $MAX_DIFF_LINES 行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AnyaSpace.Sm),
                        )
                    }
                }
            }
            if (onDownload != null) {
                AnyaSecondaryButton(
                    text = "下载此文件",
                    onClick = onDownload,
                )
            }
        }
    }
}
