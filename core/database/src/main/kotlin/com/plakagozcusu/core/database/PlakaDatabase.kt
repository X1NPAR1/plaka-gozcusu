package com.plakagozcusu.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.plakagozcusu.core.database.dao.ObservationDao
import com.plakagozcusu.core.database.entity.DiagnosticEventEntity
import com.plakagozcusu.core.database.entity.ObservationEntity
import com.plakagozcusu.core.database.entity.SessionEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [ObservationEntity::class, SessionEntity::class, DiagnosticEventEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PlakaDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao

    companion object {
        const val NAME = "plaka-gozcusu.db"

        /** v2 (0.3.0): optional AI second-opinion columns; all nullable, existing rows untouched. */
        val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE observations ADD COLUMN ai_plate_text TEXT")
                    db.execSQL("ALTER TABLE observations ADD COLUMN ai_provider TEXT")
                    db.execSQL("ALTER TABLE observations ADD COLUMN ai_checked_at INTEGER")
                    db.execSQL("ALTER TABLE observations ADD COLUMN ai_agrees INTEGER")
                }
            }

        /** Explicit migrations only; destructive fallback is forbidden (it would delete evidence). */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    @Suppress("SpreadOperator") // Runs once at startup with a tiny array.
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PlakaDatabase =
        Room
            .databaseBuilder(context, PlakaDatabase::class.java, PlakaDatabase.NAME)
            .addMigrations(*PlakaDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideObservationDao(db: PlakaDatabase): ObservationDao = db.observationDao()
}
