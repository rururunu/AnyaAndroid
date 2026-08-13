package ai.anya.companion.core.data.repository

import ai.anya.companion.core.common.di.IoDispatcher
import ai.anya.companion.core.common.result.AnyaError
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.UpdateRepository
import ai.anya.companion.core.model.update.AppUpdateInfo
import ai.anya.companion.core.model.update.UpdateCheckResult
import ai.anya.companion.core.model.update.isNewerAppVersion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@Singleton
public class DefaultUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UpdateRepository {

    override suspend fun check(
        currentVersion: String,
        currentVersionCode: Long,
    ): UpdateCheckResult = withContext(io) {
        val (remote, failure) = fetchManifest()
        if (remote == null) {
            return@withContext UpdateCheckResult.Unavailable(failure ?: "no-release")
        }
        val remoteCode = remote.versionCode
        val newerByCode = remoteCode != null && remoteCode > currentVersionCode
        val newerByName = isNewerAppVersion(remote.version, currentVersion)
        if (newerByCode || newerByName) {
            UpdateCheckResult.Available(remote)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    override suspend fun downloadApk(
        url: String,
        destFile: java.io.File,
        expectedSize: Long?,
        onProgress: (written: Long, total: Long) -> Unit,
    ): AnyaResult<java.io.File> = withContext(io) {
        destFile.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/octet-stream")
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AnyaResult.Failure(
                        AnyaError.Network("download failed: HTTP ${response.code}"),
                    )
                }
                val body = response.body ?: return@withContext AnyaResult.Failure(
                    AnyaError.Network("empty update body"),
                )
                val declared = body.contentLength()
                val totalHint = when {
                    declared > 0 -> declared
                    expectedSize != null && expectedSize > 0 -> expectedSize
                    else -> -1L
                }
                var written = 0L
                destFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            written += n
                            onProgress(written, totalHint)
                        }
                    }
                }
                return@withContext when {
                    written <= 0L -> {
                        destFile.delete()
                        AnyaResult.Failure(AnyaError.Unknown("empty-apk"))
                    }
                    declared > 0 && written != declared -> {
                        destFile.delete()
                        AnyaResult.Failure(AnyaError.Unknown("size-mismatch"))
                    }
                    expectedSize != null && expectedSize > 0 && written != expectedSize -> {
                        destFile.delete()
                        AnyaResult.Failure(AnyaError.Unknown("size-mismatch"))
                    }
                    else -> AnyaResult.Success(destFile)
                }
            }
        } catch (t: Throwable) {
            destFile.delete()
            AnyaResult.Failure(AnyaError.Network(t.message ?: "download failed", t))
        }
    }

    private fun fetchManifest(): Pair<AppUpdateInfo?, String?> {
        val fromJson = fetchLatestJson()
        if (fromJson.first != null) return fromJson
        val fromGithub = fetchGithubRelease()
        if (fromGithub.first != null) return fromGithub
        val reason = listOfNotNull(fromJson.second, fromGithub.second)
            .firstOrNull { it != "no-release" }
            ?: "no-release"
        return null to reason
    }

    private fun fetchLatestJson(): Pair<AppUpdateInfo?, String?> {
        val fetched = httpGet(MANIFEST_URL)
        val body = fetched.body ?: return null to fetched.error
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val version = obj.string("version") ?: return null to "invalid-manifest"
            val apkUrl = obj.string("apkUrl")
                ?: obj.string("url")
                ?: platformApkUrl(obj)
                ?: return null to "invalid-manifest"
            AppUpdateInfo(
                version = version,
                versionCode = obj["versionCode"]?.jsonPrimitive?.longOrNull,
                notes = obj.string("notes").orEmpty(),
                apkUrl = apkUrl,
                sizeBytes = obj["sizeBytes"]?.jsonPrimitive?.longOrNull,
                sha256 = obj.string("sha256"),
            ) to null
        }.getOrElse {
            Timber.w(it, "parse latest.json")
            null to "invalid-manifest"
        }
    }

    private fun platformApkUrl(obj: JsonObject): String? {
        val platforms = obj["platforms"]?.jsonObject ?: return null
        val preferred = platforms["android"]
            ?: platforms["android-arm64"]
            ?: platforms["android-arm64-v8a"]
            ?: platforms.values.firstOrNull()
        return preferred?.jsonObject?.string("url")
    }

    private fun fetchGithubRelease(): Pair<AppUpdateInfo?, String?> {
        val fetched = httpGet(GITHUB_RELEASE_URL)
        val body = fetched.body ?: return null to fetched.error
        val release = runCatching {
            json.decodeFromString(GithubRelease.serializer(), body)
        }.onFailure { Timber.w(it, "parse GitHub release") }.getOrNull()
            ?: return null to "invalid-manifest"
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null to "no-release"
        return AppUpdateInfo(
            version = release.tagName,
            notes = release.body.orEmpty(),
            apkUrl = apk.url,
            sizeBytes = apk.size.takeIf { it > 0 },
        ) to null
    }

    private fun httpGet(url: String): HttpGet {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> HttpGet(null, "no-release")
                    !response.isSuccessful -> HttpGet(null, "HTTP ${response.code}")
                    else -> HttpGet(response.body?.string(), null)
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "update check failed: $url")
            HttpGet(null, t.message ?: "network")
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val USER_AGENT = "AnyaCompanion"
        const val MANIFEST_URL =
            "https://github.com/rururunu/AnyaAndroid/releases/latest/download/latest.json"
        const val GITHUB_RELEASE_URL =
            "https://api.github.com/repos/rururunu/AnyaAndroid/releases/latest"
    }
}

private data class HttpGet(val body: String?, val error: String?)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)
