package ai.anya.companion.core.common.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class MainDispatcher
