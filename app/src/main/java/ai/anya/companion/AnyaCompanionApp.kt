package ai.anya.companion

import android.app.Application
import ai.anya.companion.notify.CompanionNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class AnyaCompanionApp : Application() {
    @Inject
    lateinit var notifier: CompanionNotifier

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        notifier.start(this)
    }
}
