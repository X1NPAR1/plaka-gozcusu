package com.plakagozcusu.app.di

import com.plakagozcusu.vision.api.PlateReader
import com.plakagozcusu.vision.models.MlKitPlateReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Only :app knows the concrete adapters; features depend on :vision:api interfaces. */
@Module
@InstallIn(SingletonComponent::class)
object VisionModule {
    /** Unscoped: each caller owns (and closes) its reader. */
    @Provides
    fun providePlateReader(): PlateReader = MlKitPlateReader()
}
