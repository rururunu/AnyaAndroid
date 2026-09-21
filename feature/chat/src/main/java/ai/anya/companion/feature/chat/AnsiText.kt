package ai.anya.companion.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Small ANSI parser, ported from the desktop `ansi.ts`: applies SGR (colour / bold /
 * italic / underline) and strips every other control sequence (cursor moves, clears,
 * OSC titles) so terminal cards render coloured output.
 */
internal data class AnsiSpan(
    val text: String,
    val color: Color? = null,
    val background: Color? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

/** 16-colour palette tuned for a dark terminal background (GitHub Dark). */
/** Terminal chrome for the ShellTerminalCard body (the desktop dark terminal look). */
internal val TerminalForeground: Color = Color(0xFFC9D1D9)
internal val TerminalBackground: Color = Color(0xFF0D1117)

private val PALETTE_NORMAL = listOf(
    Color(0xFF6E7681), Color(0xFFFF7B72), Color(0xFF3FB950), Color(0xFFD29922),
    Color(0xFF58A6FF), Color(0xFFBC8CFF), Color(0xFF39C5CF), Color(0xFFB1BAC4),
)
private val PALETTE_BRIGHT = listOf(
    Color(0xFF8B949E), Color(0xFFFFA198), Color(0xFF56D364), Color(0xFFE3B341),
    Color(0xFF79C0FF), Color(0xFFD2A8FF), Color(0xFF56D4DD), Color(0xFFF0F6FC),
)

/** CSI (`ESC[…X`), OSC (`ESC]…BEL/ST`) and single-character escapes. */
private val ANSI_TOKEN = Regex("\u001B\\[([0-9;:]*)([A-Za-z])|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)?|\u001B[@-Z\\\\-_]")

internal fun hasAnsi(input: String): Boolean = input.contains('\u001B')

private fun color256(index: Int): Color {
    if (index < 8) return PALETTE_NORMAL[index]
    if (index < 16) return PALETTE_BRIGHT[index - 8]
    if (index < 232) {
        val value = index - 16
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        val r = steps[(value / 36) % 6]
        val g = steps[(value / 6) % 6]
        val b = steps[value % 6]
        return Color(r, g, b)
    }
    val gray = (8 + (index - 232) * 10).coerceIn(0, 255)
    return Color(gray, gray, gray)
}

private class SgrState {
    var color: Color? = null
    var background: Color? = null
    var bold = false
    var dim = false
    var italic = false
    var underline = false

    fun apply(params: List<Int>) {
        val codes = if (params.isEmpty()) listOf(0) else params
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> { color = null; background = null; bold = false; dim = false; italic = false; underline = false }
                1 -> bold = true
                2 -> dim = true
                3 -> italic = true
                4 -> underline = true
                22 -> { bold = false; dim = false }
                23 -> italic = false
                24 -> underline = false
                39 -> color = null
                49 -> background = null
                38, 48 -> {
                    val mode = codes.getOrNull(i + 1)
                    val value: Color? = when {
                        mode == 5 && codes.getOrNull(i + 2) != null -> {
                            val c = color256(codes[i + 2].coerceIn(0, 255)); i += 2; c
                        }
                        mode == 2 && codes.getOrNull(i + 4) != null -> {
                            val c = Color(codes[i + 2].coerceIn(0, 255), codes[i + 3].coerceIn(0, 255), codes[i + 4].coerceIn(0, 255))
                            i += 4
                            c
                        }
                        else -> null
                    }
                    if (value != null) {
                        if (code == 38) color = value else background = value
                    }
                }
                in 30..37 -> color = PALETTE_NORMAL[code - 30]
                in 90..97 -> color = PALETTE_BRIGHT[code - 90]
                in 40..47 -> background = PALETTE_NORMAL[code - 40]
                in 100..107 -> background = PALETTE_BRIGHT[code - 100]
            }
            i += 1
        }
    }

    fun span(text: String) = AnsiSpan(text, color, background, bold, dim, italic, underline)
}

/** `\r\n` → `\n`; a lone `\r` inside a line rewrites it (progress bars keep the final frame). */
private fun collapseCarriageReturns(input: String): String =
    input.replace("\r\n", "\n").split('\n').joinToString("\n") { line ->
        val idx = line.lastIndexOf('\r')
        if (idx >= 0) line.substring(idx + 1) else line
    }

internal fun parseAnsi(input: String): List<AnsiSpan> {
    val source = collapseCarriageReturns(input)
    val spans = mutableListOf<AnsiSpan>()
    val state = SgrState()
    var cursor = 0
    fun push(text: String) {
        if (text.isEmpty()) return
        val span = state.span(text)
        val last = spans.lastOrNull()
        if (last != null && last.copy(text = "") == span.copy(text = "")) {
            spans[spans.lastIndex] = last.copy(text = last.text + text)
        } else {
            spans += span
        }
    }
    for (match in ANSI_TOKEN.findAll(source)) {
        push(source.substring(cursor, match.range.first))
        cursor = match.range.last + 1
        if (match.groupValues.getOrNull(2) == "m") {
            val params = match.groupValues[1].split(';', ':').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            state.apply(params)
        }
    }
    push(source.substring(cursor))
    return spans
}

internal fun stripAnsi(input: String): String = parseAnsi(input).joinToString("") { it.text }

/** Render parsed spans; [defaultColor] is the terminal foreground. */
internal fun List<AnsiSpan>.toAnnotatedString(defaultColor: Color): AnnotatedString = buildAnnotatedString {
    for (span in this@toAnnotatedString) {
        val base = span.color ?: defaultColor
        val color = if (span.dim) base.copy(alpha = base.alpha * 0.6f) else base
        val style = SpanStyle(
            color = color,
            background = span.background ?: Color.Unspecified,
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = if (span.underline) TextDecoration.Underline else null,
        )
        pushStyle(style)
        append(span.text)
        pop()
    }
}
