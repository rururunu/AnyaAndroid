package ai.anya.companion.feature.pairing

import android.net.Uri

/**
 * Parses desktop QR / deep-link payloads:
 * `anya://pair?v=1&host=...&port=8787&token=...&scheme=ws&code=...`
 */
public data class PairLink(
    public val host: String,
    public val port: Int,
    public val token: String,
    public val scheme: String = "ws",
    public val lanHost: String? = null,
    public val lanPort: Int? = null,
)

public fun parsePairLink(raw: String): PairLink? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val isAnyaPair =
        (uri.scheme.equals("anya", ignoreCase = true) && uri.host.equals("pair", ignoreCase = true)) ||
            (uri.scheme.equals("anya-companion", ignoreCase = true) &&
                uri.host.equals("pair", ignoreCase = true))
    if (!isAnyaPair) {
        // Allow bare token paste into the scanner result by rejecting non-links here.
        return null
    }

    val host = uri.getQueryParameter("host")?.trim().orEmpty()
    val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8787
    val token = (
        uri.getQueryParameter("token")
            ?: uri.getQueryParameter("code")
            ?: ""
        ).trim().replace("-", "")
    val scheme = uri.getQueryParameter("scheme")?.trim()?.ifBlank { null } ?: "ws"
    val lanHost = uri.getQueryParameter("lanHost")?.trim()?.ifBlank { null }
    val lanPort = uri.getQueryParameter("lanPort")?.toIntOrNull()?.takeIf { it in 1..65535 }
    if (host.isBlank() || token.isBlank()) return null
    return PairLink(
        host = host,
        port = port,
        token = token,
        scheme = scheme,
        lanHost = lanHost,
        lanPort = lanPort,
    )
}

/** Default port of the Anya desktop remote gateway. */
public const val DefaultGatewayPort: Int = 8787

public fun normalizePairingToken(raw: String): String =
    raw.trim().replace("-", "").replace(" ", "")

/** Host field parsed from manual input, tolerating pasted URLs like `wss://x.com/remote/v1`. */
public data class ManualHostInput(
    public val host: String,
    /** Port explicitly present in the pasted text, if any. */
    public val port: Int? = null,
    /** "ws"/"wss" when the pasted text carried a scheme, else null. */
    public val scheme: String? = null,
)

/**
 * Accepts a bare host ("192.168.1.5"), "host:port", or a full URL pasted from the
 * desktop pairing page ("ws://192.168.1.5:8787/remote/v1", "wss://xx.trycloudflare.com/remote/v1").
 * Returns null when nothing usable remains.
 */
public fun parseManualHostInput(raw: String): ManualHostInput? {
    var text = raw.trim()
    if (text.isEmpty()) return null

    var scheme: String? = null
    val schemeMatch = Regex("^(wss?|https?)://", RegexOption.IGNORE_CASE).find(text)
    if (schemeMatch != null) {
        scheme = when (schemeMatch.groupValues[1].lowercase()) {
            "wss", "https" -> "wss"
            else -> "ws"
        }
        text = text.substring(schemeMatch.value.length)
    }
    // Drop path/query such as /remote/v1.
    text = text.substringBefore('/').substringBefore('?')

    var port: Int? = null
    val colon = text.lastIndexOf(':')
    // A single colon means host:port; more than one is an IPv6 literal — leave it alone.
    if (colon > 0 && colon == text.indexOf(':')) {
        val parsed = text.substring(colon + 1).toIntOrNull()
        if (parsed != null && parsed in 1..65535) {
            port = parsed
            text = text.substring(0, colon)
        }
    }
    if (text.isBlank()) return null
    return ManualHostInput(host = text, port = port, scheme = scheme)
}
