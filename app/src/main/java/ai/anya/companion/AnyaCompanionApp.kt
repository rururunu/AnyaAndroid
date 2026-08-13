package ai.anya.companion

import android.app.Application
import android.content.Context
import ai.anya.companion.core.data.local.AppLocale
import ai.anya.companion.notify.CompanionNotifier
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class AnyaCompanionApp : Application() {
    @Inject
    lateinit var notifier: CompanionNotifier

    @Inject
    lateinit var updateMonitor: AppUpdateMonitor

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        notifier.start(this)
        updateMonitor.start()
    }
}
