package com.plakagozcusu.core.camera.di

import com.plakagozcusu.core.camera.CameraController
import com.plakagozcusu.core.camera.CameraXController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraModule {
    // Singleton: one physical camera; calibration and the monitoring service must share its state.
    @Binds
    @Singleton
    abstract fun bindCameraController(impl: CameraXController): CameraController
}
