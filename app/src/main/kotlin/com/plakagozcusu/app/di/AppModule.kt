package com.plakagozcusu.app.di

import com.plakagozcusu.core.common.AppLogger
import com.plakagozcusu.core.common.DefaultDispatcherProvider
import com.plakagozcusu.core.common.DispatcherProvider
import com.plakagozcusu.core.common.SystemTimeSource
import com.plakagozcusu.core.common.TimeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindLogger(impl: LogcatAppLogger): AppLogger

    companion object {
        @Provides
        fun provideTimeSource(): TimeSource = SystemTimeSource

        @Provides
        fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider
    }
}
