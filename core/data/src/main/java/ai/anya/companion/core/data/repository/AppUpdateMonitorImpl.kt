package ai.anya.companion.core.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ai.anya.companion.core.common.di.ApplicationScope
import ai.anya.companion.core.common.result.AnyaError
import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import ai.anya.companion.core.domain.repository.UpdateRepository
import ai.anya.companion.core.model.update.AppUpdateInfo
import ai.anya.companion.core.model.update.UpdateCheckResult
import ai.anya.companion.core.model.update.UpdateDownloadState
import ai.anya.companion.core.model.update.UpdateDownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
public class DefaultAppUpdateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : AppUpdateMonitor {

    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    private val versionName = packageInfo.versionName.orEmpty().ifBlank { "0.0.0" }
    private val versionCode = if (Build.VERSION.SDK_INT >= 28) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val started = AtomicBoolean(false)
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()
    private var lastCheckAtMs = 0L

    private val _available = MutableStateFlow<AppUpdateInfo?>(null)
    override val available: StateFlow<AppUpdateInfo?> = _available.asStateFlow()

    private val _checking = MutableStateFlow(false)
    override val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _download = MutableStateFlow(UpdateDownloadState())
    override val download: StateFlow<UpdateDownloadState> = _download.asStateFlow()

    private val _badgeVersion = MutableStateFlow<String?>(null)
    override val badgeVersion: StateFlow<String?> = _badgeVersion.asStateFlow()

    private val _newlyAvailable = MutableSharedFlow<AppUpdateInfo>(extraBufferCapacity = 1)
    override val newlyAvailable: SharedFlow<AppUpdateInfo> = _newlyAvailable.asSharedFlow()

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch { checkInternal(force = true) }
    }

    override fun onForeground() {
        if (!started.get()) return
        appScope.launch { checkInternal(force = false) }
    }

    override suspend fun checkNow(): UpdateCheckResult = checkInternal(force = true)

    override fun startDownload() {
        val info = _available.value ?: return
        if (_download.value.status != UpdateDownloadStatus.Downloading) {
            _download.value = UpdateDownloadState(
                status = UpdateDownloadStatus.Downloading,
                downloadedBytes = 0,
                totalBytes = info.sizeBytes ?: 0L,
                metered = isMetered(),
            )
        }
        runCatching {
            val intent = Intent().setClassName(context.packageName, DOWNLOAD_SERVICE_CLASS)
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { Timber.w(it, "start update download service") }
    }

    override suspend fun executeDownload() {
        downloadMutex.withLock {
            val info = _available.value ?: return
            if (_download.value.status == UpdateDownloadStatus.Ready) {
                val existing = _download.value.apkPath?.let(::File)
                if (existing != null && existing.exists() && existing.length() > 0L) return
            }
            _download.value = UpdateDownloadState(
                status = UpdateDownloadStatus.Downloading,
                downloadedBytes = 0,
                totalBytes = info.sizeBytes ?: 0L,
                metered = isMetered(),
            )
            val dest = File(context.cacheDir, "updates/Anya-update.apk")
            when (
                val result = updateRepository.downloadApk(
                    url = info.apkUrl,
                    destFile = dest,
                    expectedSize = info.sizeBytes,
                ) { written, total ->
                    _download.value = _download.value.copy(
                        status = UpdateDownloadStatus.Downloading,
                        downloadedBytes = written,
                        totalBytes = if (total > 0) total else info.sizeBytes ?: 0L,
                        metered = isMetered(),
                    )
                }
            ) {
                is AnyaResult.Success -> {
                    val verified = verifyApk(result.data, info)
                    if (verified != null) {
                        result.data.delete()
                        _download.value = UpdateDownloadState(
                            status = UpdateDownloadStatus.Failed,
                            error = verified,
                            metered = isMetered(),
                        )
                    } else {
                        _download.value = UpdateDownloadState(
                            status = UpdateDownloadStatus.Ready,
                            downloadedBytes = result.data.length(),
                            totalBytes = result.data.length(),
                            apkPath = result.data.absolutePath,
                            metered = isMetered(),
                        )
                    }
                }
                is AnyaResult.Failure -> {
                    val message = when (val err = result.error) {
                        is AnyaError.Unknown -> err.message
                        is AnyaError.Network -> err.message
                        else -> err.toString()
                    }
                    _download.value = UpdateDownloadState(
                        status = UpdateDownloadStatus.Failed,
                        error = message,
                        metered = isMetered(),
                    )
                }
            }
        }
    }

    override fun launchInstaller(): Boolean {
        val path = _download.value.apkPath ?: return false
        val apk = File(path)
        if (!apk.exists()) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            Timber.w(it, "launch installer")
            false
        }
    }

    override fun markNotified(version: String) {
        prefs.edit().putString(KEY_NOTIFIED_VERSION, version).apply()
    }

    override fun hasNotified(version: String): Boolean =
        prefs.getString(KEY_NOTIFIED_VERSION, null) == version

    override fun snoozeBadge() {
        val version = _available.value?.version ?: return
        prefs.edit().putString(KEY_SNOOZED_VERSION, version).apply()
        refreshBadge()
    }

    private suspend fun checkInternal(force: Boolean): UpdateCheckResult = checkMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && lastCheckAtMs > 0L && now - lastCheckAtMs < CHECK_INTERVAL_MS) {
            emitNotificationIfNeeded()
            return _available.value?.let { UpdateCheckResult.Available(it) }
                ?: UpdateCheckResult.UpToDate
        }
        _checking.value = true
        try {
            val result = updateRepository.check(versionName, versionCode)
            lastCheckAtMs = System.currentTimeMillis()
            when (result) {
                is UpdateCheckResult.Available -> {
                    _available.value = result.info
                    refreshBadge()
                    emitNotificationIfNeeded()
                }
                UpdateCheckResult.UpToDate -> {
                    _available.value = null
                    _download.value = UpdateDownloadState()
                    refreshBadge()
                }
                is UpdateCheckResult.Unavailable -> {
                    Timber.d("update check unavailable: %s", result.reason)
                }
            }
            result
        } finally {
            _checking.value = false
        }
    }

    private fun emitNotificationIfNeeded() {
        val info = _available.value ?: return
        if (hasNotified(info.version)) return
        _newlyAvailable.tryEmit(info)
    }

    private fun refreshBadge() {
        val version = _available.value?.version
        val snoozed = prefs.getString(KEY_SNOOZED_VERSION, null)
        _badgeVersion.value = if (version != null && version != snoozed) version else null
    }

    private fun isMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun verifyApk(apk: File, info: AppUpdateInfo): String? {
        if (!apk.exists() || apk.length() <= 0L) return "empty-apk"
        if (info.sizeBytes != null && info.sizeBytes!! > 0L && apk.length() != info.sizeBytes) {
            return "size-mismatch"
        }
        val expectedSha = info.sha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (expectedSha != null && sha256(apk) != expectedSha) {
            return "checksum-mismatch"
        }
        val archive = packageArchiveInfo(apk) ?: return "package-mismatch"
        if (archive.packageName != context.packageName) return "package-mismatch"
        if (!signaturesMatch(archive)) return "signature-mismatch"
        return null
    }

    private fun packageArchiveInfo(apk: File): android.content.pm.PackageInfo? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, flags)
        }
    }

    private fun signaturesMatch(archive: android.content.pm.PackageInfo): Boolean {
        val archiveSigners = archiveSigners(archive) ?: return true
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            }
        }.getOrNull() ?: return true
        val installedSigners = archiveSigners(installed) ?: return true
        return archiveSigners.any { remote ->
            installedSigners.any { local -> remote.contentEquals(local) }
        }
    }

    private fun archiveSigners(info: android.content.pm.PackageInfo): Array<ByteArray>? {
        val signing = info.signingInfo ?: return null
        val sigs = if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners
        } else {
            signing.signingCertificateHistory ?: signing.apkContentsSigners
        } ?: return null
        if (sigs.isEmpty()) return null
        return sigs.map { it.toByteArray() }.toTypedArray()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PREFS_NAME = "anya_updates"
        const val KEY_NOTIFIED_VERSION = "notified_version"
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val DOWNLOAD_SERVICE_CLASS = "ai.anya.companion.UpdateDownloadService"
        val CHECK_INTERVAL_MS = 6.hours.inWholeMilliseconds
    }
}
