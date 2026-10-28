package com.plakagozcusu.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.common.DispatcherProvider
import com.plakagozcusu.core.common.TimeSource
import com.plakagozcusu.core.database.PlakaDatabase
import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.ObservationQuery
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.TravelDirection
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.core.storage.EvidenceFileStore
import com.plakagozcusu.core.storage.RetentionPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ObservationRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var nowMs = 1_793_188_800_000L
    private val time =
        object : TimeSource {
            override fun now(): Instant = Instant.ofEpochMilli(nowMs)

            override fun zone(): ZoneId = ZoneId.of("Europe/Istanbul")

            override fun monotonicNanos(): Long = nowMs * 1_000_000
        }
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlakaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val files by lazy { EvidenceFileStore(tmp.root) }
    private val repo by lazy { ObservationRepository(db, db.observationDao(), files, time, dispatchers) }

    @After
    fun tearDown() = db.close()

    private fun observation(
        id: String,
        at: Long = nowMs,
        plate: String? = "34 AB 123",
    ) = Observation(
        id = id,
        sessionId = "s",
        trackId = 1,
        plateText = plate,
        originalPlateText = null,
        rawOcrText = plate,
        perCharConfidence = listOf(0.9f, 0.8f),
        plateConfidence = 0.8f,
        detectionConfidence = 0.9f,
        qualityScore = 0.7f,
        recognitionStatus = RecognitionStatus.NEEDS_REVIEW,
        motionStatus = MotionStatus.MOVING,
        direction = TravelDirection.LEFT_TO_RIGHT,
        vehicleClass = VehicleClass.CAR,
        firstSeenAt = at,
        bestFrameAt = at,
        lastSeenAt = at,
        zoneId = "Europe/Istanbul",
        scene = null,
        vehicleCrop = null,
        plateCrop = null,
        modelVersions = "test",
        appVersion = "test",
        userNote = null,
        isStarred = false,
        isProtected = false,
        createdAt = at,
        updatedAt = at,
    )

    private suspend fun saved(o: Observation) =
        (repo.save(o, EvidenceBytes(byteArrayOf(1, 2, 3), byteArrayOf(4), null)) as AppResult.Success).value

    @Test
    fun `save writes files with hashes and round-trips the record`() =
        runTest {
            val stored = saved(observation("a"))

            val back = repo.query(ObservationQuery()).single()
            assertThat(back).isEqualTo(stored)
            assertThat(back.scene!!.sha256).hasLength(64)
            assertThat(back.plateCrop).isNull()
            assertThat(repo.verifyEvidence(back)).containsExactly("scene", true, "vehicle", true)
        }

    @Test
    fun `failed row insert removes the files it wrote`() =
        runTest {
            saved(observation("dup"))
            val before = files.listRelativePaths()

            val second = repo.save(observation("dup"), EvidenceBytes(byteArrayOf(9), null, null))

            assertThat(second).isInstanceOf(AppResult.Failure::class.java)
            // The original record's files are still intact.
            assertThat(files.listRelativePaths()).isEqualTo(before)
        }

    @Test
    fun `delete removes rows and their evidence files`() =
        runTest {
            val stored = saved(observation("a"))

            assertThat(repo.delete(listOf("a"))).isEqualTo(1)
            assertThat(files.exists(stored.scene!!)).isFalse()
            assertThat(repo.query(ObservationQuery())).isEmpty()
        }

    @Test
    fun `correction keeps the original automated text and raw OCR`() =
        runTest {
            saved(observation("a"))

            repo.correctPlate("a", "34 ab 128")

            val o = repo.query(ObservationQuery()).single()
            assertThat(o.plateText).isEqualTo("34 AB 128")
            assertThat(o.originalPlateText).isEqualTo("34 AB 123")
            assertThat(o.rawOcrText).isEqualTo("34 AB 123")
            assertThat(o.recognitionStatus).isEqualTo(RecognitionStatus.USER_CORRECTED)
        }

    @Test
    fun `confirming the same text marks it user verified`() =
        runTest {
            saved(observation("a"))

            repo.correctPlate("a", "34 AB 123")

            assertThat(
                repo.query(ObservationQuery()).single().recognitionStatus,
            ).isEqualTo(RecognitionStatus.USER_VERIFIED)
        }

    @Test
    fun `maintenance deletes expired unprotected records and old orphans only`() =
        runTest {
            val day = 24L * 60 * 60 * 1000
            saved(observation("old", at = nowMs - 40 * day))
            saved(observation("kept", at = nowMs - 40 * day))
            repo.setProtected(listOf("kept"), true)
            val orphan =
                File(tmp.root, "2026/01/01/orphan.jpg").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(1))
                }
            orphan.setLastModified(nowMs - day)

            val report = repo.runMaintenance(RetentionPolicy(retentionDays = 30, storageCapBytes = 1_000_000))

            assertThat(report.deletedRecords).isEqualTo(1)
            assertThat(report.deletedOrphans).isEqualTo(1)
            assertThat(repo.query(ObservationQuery()).map { it.id }).containsExactly("kept")
        }
}
