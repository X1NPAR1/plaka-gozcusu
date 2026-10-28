package com.plakagozcusu.core.data.di

import android.content.Context
import com.plakagozcusu.core.storage.EvidenceFileStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    /** App-private (filesDir) so evidence is not visible to other apps and excluded from backups. */
    @Provides
    @Singleton
    fun provideEvidenceStore(
        @ApplicationContext context: Context,
    ): EvidenceFileStore = EvidenceFileStore(File(context.filesDir, "evidence").apply { mkdirs() })
}
