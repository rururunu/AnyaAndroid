package ai.anya.companion.core.domain.download

/**
 * Surfaces background file-download progress/outcome to the user (implemented in
 * the app layer as system notifications). `offerId` is a stable key so progress
 * updates overwrite the same notification.
 */
public interface DownloadNotifier {
    public fun showProgress(offerId: String, name: String, percent: Int)
    public fun showDone(offerId: String, name: String)
    public fun showFailed(offerId: String, name: String)
}
