package ai.anya.companion.core.domain.download

import ai.anya.companion.core.common.result.AnyaResult
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.repository.WorkspaceRepository
import ai.anya.companion.core.model.session.SharedFileStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs desktop-shared-file downloads on a process-wide scope (not the ViewModel
 * scope), so a download keeps running after the user leaves the page or
 * backgrounds the app. Progress/outcome is surfaced via [DownloadNotifier]
 * and the in-chat file card.
 */
@Singleton
public class FileDownloadManager @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val sessionRepository: SessionRepository,
    private val notifier: DownloadNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start a background download; on completion the shared-file card is patched
     * to [SharedFileStatus.Ready] (or [SharedFileStatus.Failed] on error).
     */
    public fun download(
        sessionId: String,
        offerId: String,
        path: String,
        workspaceId: String?,
        name: String,
    ) {
        notifier.showProgress(offerId, name, 0)
        sessionRepository.patchLocalSharedFile(sessionId, offerId) { current ->
            current.copy(status = SharedFileStatus.Pending, bytesReceived = 0L, error = null)
        }
        scope.launch {
            var lastPercent = -1
            var lastEmitAt = 0L
            when (
                val result = workspaceRepository.downloadFile(
                    path,
                    sessionId,
                    workspaceId,
                    onProgress = { written, total ->
                        val percent = if (total > 0) {
                            ((written * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        notifier.showProgress(offerId, name, percent)
                        val now = System.currentTimeMillis()
                        if (percent != lastPercent || now - lastEmitAt >= 80L) {
                            lastPercent = percent
                            lastEmitAt = now
                            sessionRepository.patchLocalSharedFile(sessionId, offerId) { current ->
                                current.copy(
                                    status = SharedFileStatus.Pending,
                                    bytesReceived = written,
                                    size = total.takeIf { it > 0 } ?: current.size,
                                )
                            }
                        }
                    },
                )
            ) {
                is AnyaResult.Success -> {
                    val file = result.data
                    sessionRepository.patchLocalSharedFile(sessionId, offerId) { current ->
                        current.copy(
                            localPath = file.localPath,
                            mime = file.mime.ifBlank { current.mime },
                            size = file.size.takeIf { it > 0 } ?: current.size,
                            bytesReceived = file.size.takeIf { it > 0 } ?: current.bytesReceived,
                            name = file.name.ifBlank { current.name },
                            status = SharedFileStatus.Ready,
                            error = null,
                        )
                    }
                    notifier.showDone(offerId, name)
                }
                is AnyaResult.Failure -> {
                    sessionRepository.patchLocalSharedFile(sessionId, offerId) { current ->
                        current.copy(
                            status = SharedFileStatus.Failed,
                            error = result.error.toString(),
                        )
                    }
                    notifier.showFailed(offerId, name)
                }
            }
        }
    }
}
