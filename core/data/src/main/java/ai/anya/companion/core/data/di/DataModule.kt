package ai.anya.companion.core.data.di

import ai.anya.companion.core.common.di.ApplicationScope
import ai.anya.companion.core.common.di.DefaultDispatcher
import ai.anya.companion.core.common.di.IoDispatcher
import ai.anya.companion.core.common.di.MainDispatcher
import ai.anya.companion.core.data.repository.DefaultApprovalRepository
import ai.anya.companion.core.data.repository.DefaultConnectionRepository
import ai.anya.companion.core.data.repository.DefaultLocaleRepository
import ai.anya.companion.core.data.repository.DefaultSessionRepository
import ai.anya.companion.core.data.repository.DefaultWorkspaceRepository
import ai.anya.companion.core.domain.repository.ApprovalRepository
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.core.domain.repository.LocaleRepository
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import ai.anya.companion.core.domain.repository.UpdateRepository
import ai.anya.companion.core.data.repository.DefaultAppUpdateMonitor
import ai.anya.companion.core.data.repository.DefaultUpdateRepository
import ai.anya.companion.core.domain.repository.SessionRepository
import ai.anya.companion.core.domain.repository.WorkspaceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public abstract class DataBindModule {
    @Binds
    @Singleton
    public abstract fun bindConnectionRepository(impl: DefaultConnectionRepository): ConnectionRepository

    @Binds
    @Singleton
    public abstract fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds
    @Singleton
    public abstract fun bindApprovalRepository(impl: DefaultApprovalRepository): ApprovalRepository

    @Binds
    @Singleton
    public abstract fun bindWorkspaceRepository(impl: DefaultWorkspaceRepository): WorkspaceRepository

    @Binds
    @Singleton
    public abstract fun bindLocaleRepository(impl: DefaultLocaleRepository): LocaleRepository

    @Binds
    @Singleton
    public abstract fun bindUpdateRepository(impl: DefaultUpdateRepository): UpdateRepository

    @Binds
    @Singleton
    public abstract fun bindAppUpdateMonitor(impl: DefaultAppUpdateMonitor): AppUpdateMonitor
}

@Module
@InstallIn(SingletonComponent::class)
public object CoroutineModule {
    @Provides
    @Singleton
    @ApplicationScope
    public fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @IoDispatcher
    public fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    public fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    public fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
