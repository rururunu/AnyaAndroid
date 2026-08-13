package ai.anya.companion.core.model.update

/** A published Companion APK the app can download and install. */
public data class AppUpdateInfo(
    public val version: String,
    public val versionCode: Long? = null,
    public val notes: String = "",
    public val apkUrl: String,
    public val sizeBytes: Long? = null,
    public val sha256: String? = null,
)

public sealed class UpdateCheckResult {
    public data class Available(public val info: AppUpdateInfo) : UpdateCheckResult()
    public data object UpToDate : UpdateCheckResult()
    public data class Unavailable(public val reason: String) : UpdateCheckResult()
}

public enum class UpdateDownloadStatus {
    Idle,
    Downloading,
    Ready,
    Failed,
}

public data class UpdateDownloadState(
    public val status: UpdateDownloadStatus = UpdateDownloadStatus.Idle,
    public val downloadedBytes: Long = 0,
    public val totalBytes: Long = 0,
    public val error: String? = null,
    public val apkPath: String? = null,
    public val metered: Boolean = false,
)

/**
 * Compare dotted versions (`1.2.3`, optional `v` prefix / `-debug` suffix).
 * Returns true when [remote] should replace [local].
 */
public fun isNewerAppVersion(remote: String, local: String): Boolean {
    val remoteParts = versionParts(remote)
    val localParts = versionParts(local)
    val size = maxOf(remoteParts.size, localParts.size)
    for (i in 0 until size) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r != l) return r > l
    }
    return false
}

/** Display form: `v0.1.0` or `v0.1.0-debug`. Does not add a second `v`. */
public fun formatDisplayVersion(versionName: String): String {
    val trimmed = versionName.trim()
    if (trimmed.isEmpty()) return "v0.0.0"
    return if (trimmed.startsWith("v", ignoreCase = true)) {
        "v" + trimmed.drop(1)
    } else {
        "v$trimmed"
    }
}

private fun versionParts(raw: String): List<Int> {
    val core = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    if (core.isEmpty()) return listOf(0)
    return core.split('.').map { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
}
