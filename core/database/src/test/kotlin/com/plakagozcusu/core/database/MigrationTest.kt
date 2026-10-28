package com.plakagozcusu.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a real v1 database from the committed schema (schemas/.../1.json), inserts a row, migrates
 * with the production migrations and opens it with Room v2, which validates the full schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    private fun schemaV1(): JSONObject {
        val file = File("schemas").walkTopDown().first { it.name == "1.json" }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun createV1() {
        val db = schemaV1()
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(dbName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        val w = helper.writableDatabase
        val entities = db.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            w.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                w.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
        }
        val setup = db.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) w.execSQL(setup.getString(i))
        w.execSQL(
            "INSERT INTO observations (id, session_id, track_id, plate_text, plate_confidence, detection_confidence, " +
                "quality_score, recognition_status, motion_status, direction, vehicle_class, first_seen_at, best_frame_at, " +
                "last_seen_at, zone_id, model_versions, app_version, is_starred, is_protected, created_at, updated_at) " +
                "VALUES ('old', 's', 1, '34 AB 123', 0.5, 0.9, 0.5, 'NEEDS_REVIEW', 'MOVING', 'UNKNOWN', 'CAR', 1, 1, 1, " +
                "'Europe/Istanbul', 'm', '0.2.0', 0, 1, 1, 1)",
        )
        helper.close()
    }

    @Test
    fun `v1 database migrates to v2 keeping existing evidence records`() =
        runTest {
            context.deleteDatabase(dbName)
            createV1()

            val room =
                Room
                    .databaseBuilder(context, PlakaDatabase::class.java, dbName)
                    .addMigrations(*PlakaDatabase.MIGRATIONS)
                    .allowMainThreadQueries()
                    .build()
            val row = room.observationDao().get("old")
            room.close()

            assertThat(row).isNotNull()
            assertThat(row!!.plateText).isEqualTo("34 AB 123")
            assertThat(row.isProtected).isTrue()
            assertThat(row.aiProvider).isNull()
        }
}
