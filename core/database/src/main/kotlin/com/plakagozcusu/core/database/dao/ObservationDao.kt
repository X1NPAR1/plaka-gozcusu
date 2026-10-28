package com.plakagozcusu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.plakagozcusu.core.database.entity.DiagnosticEventEntity
import com.plakagozcusu.core.database.entity.ObservationEntity
import com.plakagozcusu.core.database.entity.SessionEntity
import com.plakagozcusu.core.model.ObservationQuery
import kotlinx.coroutines.flow.Flow

/** Lightweight projection for retention planning. */
data class RetentionRow(
    val id: String,
    val createdAt: Long,
    val totalSize: Long,
    val isProtected: Boolean,
)

@Dao
interface ObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ObservationEntity)

    @Upsert
    suspend fun upsert(entity: ObservationEntity)

    @Query("SELECT * FROM observations WHERE id = :id")
    suspend fun get(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE id = :id")
    fun observe(id: String): Flow<ObservationEntity?>

    @RawQuery(observedEntities = [ObservationEntity::class])
    fun observeQuery(query: SupportSQLiteQuery): Flow<List<ObservationEntity>>

    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<ObservationEntity>

    @Query("SELECT * FROM observations ORDER BY best_frame_at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<ObservationEntity>>

    @Query(
        "SELECT recognition_status AS status, COUNT(*) AS count FROM observations " +
            "WHERE best_frame_at >= :from AND best_frame_at < :to GROUP BY recognition_status",
    )
    fun observeStatusCounts(
        from: Long,
        to: Long,
    ): Flow<List<StatusCount>>

    @Query(
        "SELECT id, created_at AS createdAt, " +
            "COALESCE(scene_size,0)+COALESCE(vehicle_size,0)+COALESCE(plate_size,0) AS totalSize, " +
            "is_protected AS isProtected FROM observations",
    )
    suspend fun retentionRows(): List<RetentionRow>

    @Query(
        "SELECT scene_path FROM observations WHERE scene_path IS NOT NULL " +
            "UNION SELECT vehicle_path FROM observations WHERE vehicle_path IS NOT NULL " +
            "UNION SELECT plate_path FROM observations WHERE plate_path IS NOT NULL",
    )
    suspend fun allEvidencePaths(): List<String>

    @Query("UPDATE observations SET is_protected = :value, updated_at = :now WHERE id IN (:ids)")
    suspend fun setProtected(
        ids: List<String>,
        value: Boolean,
        now: Long,
    )

    @Query("UPDATE observations SET is_starred = :value, updated_at = :now WHERE id = :id")
    suspend fun setStarred(
        id: String,
        value: Boolean,
        now: Long,
    )

    @Query("UPDATE observations SET user_note = :note, updated_at = :now WHERE id = :id")
    suspend fun setNote(
        id: String,
        note: String?,
        now: Long,
    )

    @Query("SELECT * FROM observations WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ObservationEntity>

    @Query("DELETE FROM observations WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>): Int

    @Query(
        "UPDATE observations SET ai_plate_text = :plate, ai_provider = :provider, ai_checked_at = :at, " +
            "ai_agrees = :agrees, recognition_status = :status, updated_at = :at WHERE id = :id",
    )
    @Suppress("LongParameterList") // One bind parameter per updated column.
    suspend fun updateAi(
        id: String,
        plate: String?,
        provider: String,
        at: Long,
        agrees: Boolean,
        status: String,
    )

    /** Retention path: never deletes a row that became protected after planning. */
    @Query("SELECT * FROM observations WHERE id IN (:ids) AND is_protected = 0")
    suspend fun getUnprotected(ids: List<String>): List<ObservationEntity>

    @Query(
        "UPDATE observations SET plate_text = :plate, plate_search = :search, " +
            "original_plate_text = :original, recognition_status = :status, updated_at = :now WHERE id = :id",
    )
    @Suppress("LongParameterList") // One bind parameter per updated column.
    suspend fun updatePlate(
        id: String,
        plate: String?,
        search: String?,
        original: String?,
        status: String,
        now: Long,
    )

    @Query("SELECT COUNT(*) FROM observations")
    suspend fun count(): Int

    // Sessions
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    // Diagnostics
    @Insert
    suspend fun insertDiagnostic(event: DiagnosticEventEntity)

    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latestDiagnostics(limit: Int): List<DiagnosticEventEntity>

    @Query("DELETE FROM diagnostic_events WHERE timestamp < :before")
    suspend fun pruneDiagnostics(before: Long): Int
}

data class StatusCount(
    val status: String,
    val count: Int,
)

/** Builds the parameterised timeline/incident query; values are bound, never concatenated. */
object ObservationQueryBuilder {
    fun build(q: ObservationQuery): SimpleSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        q.fromMillis?.let {
            where += "best_frame_at >= ?"
            args += it
        }
        q.toMillis?.let {
            where += "best_frame_at < ?"
            args += it
        }
        normalizePlateForSearch(q.plateFragment)?.let {
            where += "plate_search LIKE ? ESCAPE '\\'"
            args += "%${it.replace("%", "\\%").replace("_", "\\_")}%"
        }

        fun inClause(
            column: String,
            values: Collection<String>,
        ) {
            if (values.isEmpty()) return
            where += "$column IN (${values.joinToString(",") { "?" }})"
            args.addAll(values)
        }
        inClause("recognition_status", q.statuses.map { it.name })
        inClause("direction", q.directions.map { it.name })
        inClause("vehicle_class", q.vehicleClasses.map { it.name })
        if (q.starredOnly) where += "is_starred = 1"
        if (q.protectedOnly) where += "is_protected = 1"
        val sql =
            buildString {
                append("SELECT * FROM observations")
                if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
                append(" ORDER BY best_frame_at ").append(if (q.newestFirst) "DESC" else "ASC")
            }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}

/** Uppercase, drop everything except A-Z and digits; null when nothing searchable remains. */
fun normalizePlateForSearch(text: String?): String? =
    text
        ?.uppercase(java.util.Locale.ROOT)
        ?.filter { it in 'A'..'Z' || it in '0'..'9' }
        ?.takeIf { it.isNotEmpty() }
