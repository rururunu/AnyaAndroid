package ai.anya.companion.di

import ai.anya.companion.core.domain.download.DownloadNotifier
import ai.anya.companion.notify.DownloadNotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public abstract class AppModule {
    @Binds
    @Singleton
    public abstract fun bindDownloadNotifier(
        impl: DownloadNotificationManager,
    ): DownloadNotifier
}
