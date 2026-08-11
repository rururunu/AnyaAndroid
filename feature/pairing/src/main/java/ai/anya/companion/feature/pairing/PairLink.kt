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
    if (host.isBlank() || token.isBlank()) return null
    return PairLink(host = host, port = port, token = token, scheme = scheme)
}

public fun normalizePairingToken(raw: String): String =
    raw.trim().replace("-", "").replace(" ", "")
