package ai.anya.companion.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

internal sealed class MarkdownPart {
    data class Text(val value: String) : MarkdownPart()
    data class Code(val language: String?, val code: String) : MarkdownPart()
}

private val CODE_FENCE_RE = Regex("```([^\\n`]*)\n([\\s\\S]*?)```")

internal fun splitMarkdownParts(content: String): List<MarkdownPart> {
    if (content.isBlank()) return emptyList()
    val parts = mutableListOf<MarkdownPart>()
    var cursor = 0
    CODE_FENCE_RE.findAll(content).forEach { match ->
        if (match.range.first > cursor) {
            parts += MarkdownPart.Text(content.substring(cursor, match.range.first))
        }
        val rawLang = match.groupValues[1].trim()
        val lang = rawLang.split(Regex("\\s+")).firstOrNull()?.lowercase()?.ifBlank { null }
        parts += MarkdownPart.Code(lang, match.groupValues[2])
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        parts += MarkdownPart.Text(content.substring(cursor))
    }
    return parts.filter {
        when (it) {
            is MarkdownPart.Text -> it.value.isNotBlank()
            is MarkdownPart.Code -> it.code.isNotBlank()
        }
    }
}

/**
 * Anya-styled Markdown body for chat / docs.
 */
@Composable
public fun AnyaMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    compact: Boolean = false,
) {
    if (content.isBlank()) return

    val parts = remember(content) { splitMarkdownParts(content) }
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onBackground,
        codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        inlineCodeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        tableBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
    )
    val typography = if (compact) {
        val body = MaterialTheme.typography.bodySmall
        markdownTypography(
            h1 = MaterialTheme.typography.titleMedium,
            h2 = MaterialTheme.typography.titleSmall,
            h3 = MaterialTheme.typography.titleSmall,
            h4 = body,
            h5 = body,
            h6 = body,
            text = body,
            code = body,
            quote = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
        )
    } else {
        markdownTypography(
            h1 = MaterialTheme.typography.headlineSmall,
            h2 = MaterialTheme.typography.titleLarge,
            h3 = MaterialTheme.typography.titleMedium,
            h4 = MaterialTheme.typography.titleSmall,
            h5 = textStyle,
            h6 = textStyle,
            text = textStyle,
            code = MaterialTheme.typography.bodyMedium,
            quote = textStyle,
            paragraph = textStyle,
            ordered = textStyle,
            bullet = textStyle,
            list = textStyle,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        parts.forEach { part ->
            when (part) {
                is MarkdownPart.Text -> {
                    val markdownState = rememberMarkdownState(part.value)
                    Markdown(
                        markdownState = markdownState,
                        colors = colors,
                        typography = typography,
                    )
                }
                is MarkdownPart.Code -> {
                    if (part.language == "chart") {
                        // Chart fences are handled by AnyaChatContent; fall back to JSON block.
                        AnyaCodeFenceBlock(language = "json", code = part.code)
                    } else {
                        AnyaCodeFenceBlock(language = part.language, code = part.code)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AnyaCodeFenceBlock(language: String?, code: String) {
    val label = language?.trim()?.uppercase()?.ifBlank { null } ?: "CODE"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = code.trimEnd(),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
