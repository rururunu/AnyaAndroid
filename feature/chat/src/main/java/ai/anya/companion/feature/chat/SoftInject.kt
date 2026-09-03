package ai.anya.companion.feature.chat

/** Persisted marker so soft-injects survive history reload (matches desktop). */
internal const val SOFT_INJECT_MARKER = "<!--peek:soft-inject-->"

internal fun isSoftInjectContent(content: String?): Boolean {
    if (content.isNullOrBlank()) return false
    return content.trimStart().startsWith(SOFT_INJECT_MARKER)
}

internal fun stripSoftInjectMarker(content: String): String {
    val trimmed = content.trimStart()
    if (!trimmed.startsWith(SOFT_INJECT_MARKER)) return content
    return trimmed.removePrefix(SOFT_INJECT_MARKER).trimStart()
}

internal fun markSoftInjectContent(content: String): String {
    val trimmed = content.trim()
    if (trimmed.isEmpty() || isSoftInjectContent(trimmed)) return trimmed
    return "$SOFT_INJECT_MARKER\n$trimmed"
}
