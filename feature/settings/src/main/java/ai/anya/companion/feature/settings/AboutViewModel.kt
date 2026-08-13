package ai.anya.companion.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import ai.anya.companion.core.model.update.UpdateCheckResult
import ai.anya.companion.core.model.update.UpdateDownloadStatus
import ai.anya.companion.core.model.update.formatDisplayVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public enum class AboutUpdateStatus {
    Idle,
    Checking,
    Available,
    UpToDate,
    Downloading,
    Ready,
    Error,
}

public data class AboutUiState(
    public val appName: String = "Anya",
    public val version: String = "",
    public val identifier: String = "",
    public val status: AboutUpdateStatus = AboutUpdateStatus.Idle,
    public val latestVersion: String = "",
    public val latestNotes: String = "",
    public val latestSizeBytes: Long = 0,
    public val errorMessage: String? = null,
    public val downloadedBytes: Long = 0,
    public val totalBytes: Long = 0,
    public val metered: Boolean = false,
    public val needsInstallPermission: Boolean = false,
)

@HiltViewModel
public class AboutViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val updateMonitor: AppUpdateMonitor,
) : ViewModel() {

    private val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    private val versionName = packageInfo.versionName.orEmpty().ifBlank { "0.0.0" }
    private val appName = appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
        .ifBlank { "Anya" }
    private val needsPermission = MutableStateFlow(false)
    private val lastCheckUpToDate = MutableStateFlow(false)
    private val lastCheckError = MutableStateFlow<String?>(null)

    public val state: StateFlow<AboutUiState> = combine(
        updateMonitor.available,
        updateMonitor.checking,
        updateMonitor.download,
        combine(needsPermission, lastCheckUpToDate, lastCheckError) { permission, upToDate, error ->
            Triple(permission, upToDate, error)
        },
    ) { info, checking, download, flags ->
        val (permission, upToDate, checkError) = flags
        val base = AboutUiState(
            appName = appName,
            version = formatDisplayVersion(versionName),
            identifier = appContext.packageName,
            latestVersion = info?.version?.let(::formatDisplayVersion).orEmpty(),
            latestNotes = info?.notes.orEmpty(),
            latestSizeBytes = info?.sizeBytes ?: 0L,
            downloadedBytes = download.downloadedBytes,
            totalBytes = download.totalBytes,
            metered = download.metered,
            needsInstallPermission = permission,
        )
        when {
            download.status == UpdateDownloadStatus.Downloading -> base.copy(
                status = AboutUpdateStatus.Downloading,
            )
            download.status == UpdateDownloadStatus.Ready && info != null -> base.copy(
                status = AboutUpdateStatus.Ready,
            )
            download.status == UpdateDownloadStatus.Failed -> base.copy(
                status = AboutUpdateStatus.Error,
                errorMessage = download.error,
            )
            checking && info == null -> base.copy(status = AboutUpdateStatus.Checking)
            info != null -> base.copy(status = AboutUpdateStatus.Available)
            checkError != null -> base.copy(
                status = AboutUpdateStatus.Error,
                errorMessage = checkError,
            )
            upToDate -> base.copy(status = AboutUpdateStatus.UpToDate)
            else -> base
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AboutUiState(
            appName = appName,
            version = formatDisplayVersion(versionName),
            identifier = appContext.packageName,
        ),
    )

    public fun check() {
        if (state.value.status == AboutUpdateStatus.Checking ||
            state.value.status == AboutUpdateStatus.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            when (val result = updateMonitor.checkNow()) {
                is UpdateCheckResult.Available -> {
                    lastCheckUpToDate.value = false
                    lastCheckError.value = null
                }
                UpdateCheckResult.UpToDate -> {
                    lastCheckUpToDate.value = true
                    lastCheckError.value = null
                }
                is UpdateCheckResult.Unavailable -> {
                    lastCheckUpToDate.value = false
                    lastCheckError.value = result.reason
                }
            }
        }
    }

    public fun install() {
        when (state.value.status) {
            AboutUpdateStatus.Downloading -> return
            AboutUpdateStatus.Ready -> {
                updateMonitor.launchInstaller()
            }
            else -> {
                if (!canInstallPackages()) {
                    needsPermission.value = true
                    return
                }
                updateMonitor.startDownload()
            }
        }
    }

    public fun snooze() {
        updateMonitor.snoozeBadge()
    }

    public fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }

    public fun onInstallPermissionReturned() {
        needsPermission.value = false
        if (canInstallPackages() && updateMonitor.available.value != null) {
            install()
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < 26 || appContext.packageManager.canRequestPackageInstalls()
}
