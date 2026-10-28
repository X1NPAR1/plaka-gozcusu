package com.plakagozcusu.core.data

import androidx.room.withTransaction
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.common.DispatcherProvider
import com.plakagozcusu.core.common.TimeSource
import com.plakagozcusu.core.database.PlakaDatabase
import com.plakagozcusu.core.database.dao.ObservationDao
import com.plakagozcusu.core.database.dao.ObservationQueryBuilder
import com.plakagozcusu.core.database.dao.normalizePlateForSearch
import com.plakagozcusu.core.database.entity.DiagnosticEventEntity
import com.plakagozcusu.core.database.entity.SessionEntity
import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.MonitoringSession
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.ObservationQuery
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.storage.EvidenceFileStore
import com.plakagozcusu.core.storage.RetentionCandidate
import com.plakagozcusu.core.storage.RetentionPlanner
import com.plakagozcusu.core.storage.RetentionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Bytes for one observation's evidence; crops are optional (e.g. no plate visible). */
class EvidenceBytes(
    val scene: ByteArray,
    val vehicleCrop: ByteArray?,
    val plateCrop: ByteArray?,
)

data class RetentionReport(
    val deletedRecords: Int,
    val deletedTempFiles: Int,
    val deletedOrphans: Int,
)

/**
 * Single source of truth for observations. Writes evidence files first (atomic), then the row; if
 * the row fails the files are removed, so the DB never points at missing evidence and orphans only
 * arise from process death, which [runMaintenance] cleans up.
 */
@Singleton
class ObservationRepository
    @Inject
    constructor(
        private val db: PlakaDatabase,
        private val dao: ObservationDao,
        private val files: EvidenceFileStore,
        private val time: TimeSource,
        private val dispatchers: DispatcherProvider,
    ) {
        fun observe(query: ObservationQuery): Flow<List<Observation>> =
            dao.observeQuery(query.toSql()).map { rows -> rows.map { it.toModel() } }

        suspend fun query(query: ObservationQuery): List<Observation> = dao.query(query.toSql()).map { it.toModel() }

        fun observeLatest(limit: Int): Flow<List<Observation>> = dao.observeLatest(limit).map { r -> r.map { it.toModel() } }

        fun observe(id: String): Flow<Observation?> = dao.observe(id).map { it?.toModel() }

        fun observeStatusCounts(
            fromMillis: Long,
            toMillis: Long,
        ): Flow<Map<RecognitionStatus, Int>> =
            dao.observeStatusCounts(fromMillis, toMillis).map { rows ->
                rows
                    .mapNotNull { row ->
                        RecognitionStatus.entries.firstOrNull { it.name == row.status }?.let {
                            it to
                                row.count
                        }
                    }.toMap()
            }

        suspend fun save(
            observation: Observation,
            evidence: EvidenceBytes,
        ): AppResult<Observation> =
            withContext(dispatchers.io) {
                val written = mutableListOf<EvidenceFile>()

                fun store(
                    bytes: ByteArray?,
                    suffix: String,
                ): AppResult<EvidenceFile?> {
                    if (bytes == null) return AppResult.Success(null)
                    return when (val r = files.write(bytes, observation.bestFrameAt, "${observation.id}_$suffix.jpg")) {
                        is AppResult.Success -> AppResult.Success(r.value.also { written += it })
                        is AppResult.Failure -> r
                    }
                }
                val scene = store(evidence.scene, "scene")
                val vehicle = store(evidence.vehicleCrop, "vehicle")
                val plate = store(evidence.plateCrop, "plate")
                val failure = listOf(scene, vehicle, plate).filterIsInstance<AppResult.Failure>().firstOrNull()
                if (failure != null) {
                    written.forEach(files::delete)
                    return@withContext failure
                }
                val stored =
                    observation.copy(
                        scene = (scene as AppResult.Success).value,
                        vehicleCrop = (vehicle as AppResult.Success).value,
                        plateCrop = (plate as AppResult.Success).value,
                    )
                try {
                    dao.insert(stored.toEntity())
                    AppResult.Success(stored)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: RuntimeException,
                ) {
                    written.forEach(files::delete)
                    AppResult.Failure(AppError.Io(e))
                }
            }

        /** Deletes rows (user action, protected or not) and their files. Returns rows removed. */
        suspend fun delete(ids: Collection<String>): Int = deleteRows(ids, onlyUnprotected = false)

        /**
         * Chunked so huge selections stay under SQLite's bound-variable limit; rows are re-read
         * inside the transaction so only rows actually deleted lose their files.
         */
        private suspend fun deleteRows(
            ids: Collection<String>,
            onlyUnprotected: Boolean,
        ): Int =
            withContext(dispatchers.io) {
                var removed = 0
                ids.distinct().chunked(SQL_CHUNK).forEach { chunk ->
                    val rows =
                        db.withTransaction {
                            val targets = if (onlyUnprotected) dao.getUnprotected(chunk) else dao.getAll(chunk)
                            if (targets.isNotEmpty()) dao.delete(targets.map { it.id })
                            targets
                        }
                    removed += rows.size
                    rows.flatMap { it.evidencePaths }.forEach { path -> files.resolve(path).delete() }
                }
                removed
            }

        suspend fun setProtected(
            ids: Collection<String>,
            value: Boolean,
        ) = ids.distinct().chunked(SQL_CHUNK).forEach { dao.setProtected(it, value, time.now().toEpochMilli()) }

        suspend fun setStarred(
            id: String,
            value: Boolean,
        ) = dao.setStarred(id, value, time.now().toEpochMilli())

        suspend fun setNote(
            id: String,
            note: String?,
        ) = dao.setNote(id, note?.takeIf { it.isNotBlank() }, time.now().toEpochMilli())

        /**
         * User correction. The first *automated* value is kept in originalPlateText (a later
         * correction never moves a user value there); raw OCR is never touched. Targeted update in
         * a transaction so concurrent protect/star/note changes are not reverted.
         */
        suspend fun correctPlate(
            id: String,
            corrected: String,
        ): AppResult<Unit> =
            db.withTransaction {
                val row = dao.get(id) ?: return@withTransaction AppResult.Failure(AppError.Unexpected())
                val text = corrected.trim().uppercase(java.util.Locale.ROOT).ifEmpty { null }
                val wasUser =
                    row.recognitionStatus == RecognitionStatus.USER_CORRECTED.name ||
                        row.recognitionStatus == RecognitionStatus.USER_VERIFIED.name
                val original = if (wasUser) row.originalPlateText else row.originalPlateText ?: row.plateText
                val status =
                    if (text != null && text == (original ?: row.plateText)) {
                        RecognitionStatus.USER_VERIFIED
                    } else {
                        RecognitionStatus.USER_CORRECTED
                    }
                dao.updatePlate(
                    id,
                    text,
                    normalizePlateForSearch(text),
                    original,
                    status.name,
                    time.now().toEpochMilli(),
                )
                AppResult.Success(Unit)
            }

        /** Stores an AI second opinion and the resulting status (see AiDecision). */
        suspend fun applyAiOpinion(
            id: String,
            aiPlate: String?,
            provider: String,
        ) = db.withTransaction {
            val row = dao.get(id) ?: return@withTransaction
            val current = RecognitionStatus.entries.firstOrNull { it.name == row.recognitionStatus } ?: return@withTransaction
            val (status, agrees) =
                com.plakagozcusu.core.data.ai.AiDecision
                    .apply(current, row.plateText, aiPlate)
            dao.updateAi(id, aiPlate, provider, time.now().toEpochMilli(), agrees, status.name)
        }

        suspend fun get(id: String): Observation? = dao.get(id)?.toModel()

        suspend fun correctionPairs(): List<Pair<String, String>> = dao.correctionPairs().map { it.automated to it.corrected }

        /** Plate crops the user confirmed or corrected: ground-truth samples for future training. */
        suspend fun userLabelled(): List<Observation> = dao.userLabelled().map { it.toModel() }

        /** Map of evidence kind to integrity check result for the detail screen and export. */
        suspend fun verifyEvidence(observation: Observation): Map<String, Boolean> =
            withContext(dispatchers.io) {
                listOfNotNull(
                    observation.scene?.let { "scene" to files.verify(it) },
                    observation.vehicleCrop?.let { "vehicle" to files.verify(it) },
                    observation.plateCrop?.let { "plate" to files.verify(it) },
                ).toMap()
            }

        fun resolveFile(file: EvidenceFile) = files.resolve(file.relativePath)

        suspend fun runMaintenance(policy: RetentionPolicy): RetentionReport =
            withContext(dispatchers.io) {
                val now = time.now().toEpochMilli()
                val candidates =
                    dao.retentionRows().map { RetentionCandidate(it.id, it.createdAt, it.totalSize, it.isProtected) }
                val deleted = deleteRows(RetentionPlanner.plan(candidates, policy, now), onlyUnprotected = true)
                val temps = files.sweepTempFiles(now, ORPHAN_GRACE_MS)
                val referenced = dao.allEvidencePaths().toSet()
                // Only old orphans: a file written moments ago may still be waiting for its DB row.
                val orphans =
                    (files.listRelativePaths() - referenced)
                        .map(files::resolve)
                        .filter { now - it.lastModified() > ORPHAN_GRACE_MS }
                        .count { it.delete() }
                dao.pruneDiagnostics(now - DIAGNOSTIC_RETENTION_MS)
                RetentionReport(deleted, temps, orphans)
            }

        suspend fun usedBytes(): Long = withContext(dispatchers.io) { files.totalBytes() }

        suspend fun upsertSession(session: MonitoringSession) =
            dao.upsertSession(
                SessionEntity(
                    session.id,
                    session.startedAt,
                    session.endedAt,
                    session.deviceInfo,
                    session.settingsSnapshot,
                    session.stats.framesAnalyzed,
                    session.stats.framesDropped,
                    session.stats.vehicles,
                    session.stats.platesRead,
                    session.stats.uncertain,
                    session.stats.unreadable,
                    session.endReason,
                ),
            )

        suspend fun logDiagnostic(
            stage: String,
            code: String,
            durationMs: Long?,
        ) = dao.insertDiagnostic(
            DiagnosticEventEntity(
                timestamp = time.now().toEpochMilli(),
                stage = stage,
                code = code,
                durationMs = durationMs,
            ),
        )

        private fun ObservationQuery.toSql() = ObservationQueryBuilder.build(this)

        private companion object {
            const val ORPHAN_GRACE_MS = 10 * 60 * 1000L

            /** Below SQLite's historical 999 bound-variable limit. */
            const val SQL_CHUNK = 500
            const val DIAGNOSTIC_RETENTION_MS = 14L * 24 * 60 * 60 * 1000
        }
    }
