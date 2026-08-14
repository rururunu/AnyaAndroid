package ai.anya.companion.core.model.protocol

/**
 * User-facing label for a paired desktop. Kept short so it fits the top bar.
 */
public object HostDisplayName {
    public const val MAX_LENGTH: Int = 16
    public const val DEFAULT: String = "Anya"

    public fun sanitize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH)

    public fun orFallback(displayName: String?): String =
        sanitize(displayName.orEmpty()).ifBlank { DEFAULT }

    public fun suggest(
        host: String,
        lanHost: String? = null,
        deviceName: String? = null,
        existing: String? = null,
    ): String {
        sanitize(existing.orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        sanitize(deviceName.orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        val lan = lanHost?.trim().orEmpty()
        if (lan.isNotEmpty() && !isLoopbackLanHost(lan) && !looksLikeAddress(lan)) {
            return sanitize(shortHost(lan)).ifBlank { DEFAULT }
        }
        val primary = host.trim()
        if (primary.isNotEmpty() && !looksLikeAddress(primary)) {
            return sanitize(shortHost(primary)).ifBlank { DEFAULT }
        }
        return DEFAULT
    }

    private fun shortHost(value: String): String {
        val host = value.trim().trimStart('[').trimEnd(']').trimEnd('.')
        val first = host.substringBefore('.')
        return first.ifBlank { host }
    }

    private fun looksLikeAddress(value: String): Boolean {
        val h = value.trim().trimStart('[').trimEnd(']').lowercase()
        if (h.contains("trycloudflare.com") || h.contains("cfargotunnel.com")) return true
        if (h.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return true
        if (h.contains(':') && h.any { it.isDigit() }) return true
        return false
    }
}
