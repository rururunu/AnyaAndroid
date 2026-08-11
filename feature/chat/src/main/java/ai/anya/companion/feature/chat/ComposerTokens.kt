package ai.anya.companion.feature.chat

import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary

/** Keep in sync with desktop `COMPOSER_INLINE_TOKEN_RE`. */
private val COMPOSER_INLINE_TOKEN_RE =
    Regex("""@(?:"([^"]+)"|([^\s@#]+))|#(skill|mcp):([A-Za-z0-9_.-]+)""")

internal sealed class ComposerSegment {
    data class Text(val text: String) : ComposerSegment()
    data class Mention(val path: String, val isDir: Boolean) : ComposerSegment()
    data class Skill(val id: String) : ComposerSegment()
    data class Mcp(val id: String) : ComposerSegment()
}

internal data class ParsedComposer(
    val segments: List<ComposerSegment>,
    val liveMessage: String,
)

internal fun parseComposerText(text: String): ParsedComposer {
    if (text.isEmpty()) return ParsedComposer(emptyList(), "")
    val segments = mutableListOf<ComposerSegment>()
    var lastIndex = 0
    var sawChip = false
    for (match in COMPOSER_INLINE_TOKEN_RE.findAll(text)) {
        if (match.range.first > lastIndex) {
            val between = text.substring(lastIndex, match.range.first)
            if (between.isNotBlank()) {
                segments += ComposerSegment.Text(between)
            }
        }
        val kind = match.groupValues.getOrNull(3).orEmpty()
        val resourceId = match.groupValues.getOrNull(4).orEmpty()
        if (kind.isNotEmpty() && resourceId.isNotEmpty()) {
            sawChip = true
            segments += if (kind == "skill") {
                ComposerSegment.Skill(resourceId)
            } else {
                ComposerSegment.Mcp(resourceId)
            }
        } else {
            val path = match.groupValues.getOrNull(1)
                ?.takeIf { it.isNotEmpty() }
                ?: match.groupValues.getOrNull(2).orEmpty()
            if (path.isNotEmpty()) {
                sawChip = true
                val storage = mentionStoragePath(path)
                segments += ComposerSegment.Mention(storage, isDirMention(storage))
            }
        }
        lastIndex = match.range.last + 1
    }
    if (!sawChip) {
        return ParsedComposer(emptyList(), text)
    }
    return ParsedComposer(
        segments = segments,
        liveMessage = text.substring(lastIndex).trimStart(),
    )
}

/** Parse all tokens for read-only message bubbles (no live trailing split). */
internal fun parseInlineParts(text: String): List<ComposerSegment> {
    if (text.isEmpty()) return emptyList()
    val parts = mutableListOf<ComposerSegment>()
    var lastIndex = 0
    for (match in COMPOSER_INLINE_TOKEN_RE.findAll(text)) {
        if (match.range.first > lastIndex) {
            parts += ComposerSegment.Text(text.substring(lastIndex, match.range.first))
        }
        val kind = match.groupValues.getOrNull(3).orEmpty()
        val resourceId = match.groupValues.getOrNull(4).orEmpty()
        if (kind.isNotEmpty() && resourceId.isNotEmpty()) {
            parts += if (kind == "skill") {
                ComposerSegment.Skill(resourceId)
            } else {
                ComposerSegment.Mcp(resourceId)
            }
        } else {
            val path = match.groupValues.getOrNull(1)
                ?.takeIf { it.isNotEmpty() }
                ?: match.groupValues.getOrNull(2).orEmpty()
            if (path.isNotEmpty()) {
                val storage = mentionStoragePath(path)
                parts += ComposerSegment.Mention(storage, isDirMention(storage))
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        parts += ComposerSegment.Text(text.substring(lastIndex))
    }
    return parts
}

internal fun serializeComposerSegments(
    segments: List<ComposerSegment>,
    liveMessage: String,
): String {
    val parts = mutableListOf<String>()
    for (seg in segments) {
        when (seg) {
            is ComposerSegment.Text -> parts += seg.text
            is ComposerSegment.Mention -> parts += formatMentionPath(seg.path, seg.isDir)
            is ComposerSegment.Skill -> parts += "#skill:${seg.id}"
            is ComposerSegment.Mcp -> parts += "#mcp:${seg.id}"
        }
    }
    if (liveMessage.isNotEmpty()) parts += liveMessage
    return joinInlineParts(parts)
}

internal fun joinInlineParts(parts: List<String>): String {
    var out = ""
    for (part in parts) {
        if (part.isEmpty()) continue
        if (out.isEmpty()) {
            out = part
            continue
        }
        out += if (out.last().isWhitespace() || part.first().isWhitespace()) {
            part
        } else {
            " $part"
        }
    }
    return out
}

internal fun normalizeMentionPath(path: String): String =
    path.replace('\\', '/').trim('/').trim()

internal fun isDirMention(path: String): Boolean =
    path.trim().endsWith('/') || path.trim().endsWith('\\')

internal fun mentionStoragePath(path: String): String {
    val normalized = normalizeMentionPath(path)
    if (normalized.isEmpty()) return ""
    return if (isDirMention(path) || path.trim().endsWith('/')) "$normalized/" else normalized
}

internal fun mentionBasename(path: String): String {
    val normalized = normalizeMentionPath(path)
    return normalized.substringAfterLast('/').ifEmpty { normalized }
}

internal fun formatMentionPath(path: String, isDir: Boolean): String {
    val storage = mentionStoragePath(if (isDir && !path.endsWith('/')) "$path/" else path)
    return if (storage.any { it.isWhitespace() }) "@\"$storage\"" else "@$storage"
}

internal fun prettyHashInstallId(id: String): String {
    val trimmed = id.trim()
    return when {
        trimmed.startsWith("sm-") -> trimmed.removePrefix("sm-")
        trimmed.startsWith("smid-") -> trimmed.removePrefix("smid-")
        else -> trimmed
    }
}

internal fun skillMentionLabel(id: String, skills: List<SkillSummary>): String {
    val skill = skills.find { it.name == id || it.id == id }
    return skill?.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: prettyHashInstallId(id).ifEmpty { id }
}

internal fun skillMentionIconUrl(id: String, skills: List<SkillSummary>): String? =
    skills.find { it.name == id || it.id == id }?.iconUrl?.trim()?.takeIf { it.isNotEmpty() }

internal fun mcpMentionLabel(id: String, servers: List<McpServerSummary>): String {
    val server = servers.find { it.id == id }
    return server?.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: server?.qualifiedName?.trim()?.takeIf { it.isNotEmpty() }
        ?: prettyHashInstallId(id).ifEmpty { id }
}

internal fun mcpMentionIconUrl(id: String, servers: List<McpServerSummary>): String? =
    servers.find { it.id == id }?.iconUrl?.trim()?.takeIf { it.isNotEmpty() }

internal fun mentionDisplayLabel(path: String, isDir: Boolean): String {
    val normalized = normalizeMentionPath(path)
    if (normalized.isEmpty()) return path
    if (isDir) return normalized
    return mentionBasename(normalized)
}
