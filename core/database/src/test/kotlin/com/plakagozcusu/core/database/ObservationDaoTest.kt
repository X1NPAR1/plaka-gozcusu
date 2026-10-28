package com.plakagozcusu.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.database.dao.ObservationQueryBuilder
import com.plakagozcusu.core.database.entity.ObservationEntity
import com.plakagozcusu.core.model.ObservationQuery
import com.plakagozcusu.core.model.RecognitionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationDaoTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlakaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dao = db.observationDao()

    @After
    fun tearDown() = db.close()

    private fun entity(
        id: String,
        at: Long,
        plate: String? = null,
        status: String = "NEEDS_REVIEW",
        protected: Boolean = false,
    ) = ObservationEntity(
        id = id,
        sessionId = "s",
        trackId = 1,
        plateText = plate,
        originalPlateText = null,
        plateSearch = plate?.filter { it.isLetterOrDigit() },
        rawOcrText = plate,
        perCharConfidence = null,
        plateConfidence = 0.5f,
        detectionConfidence = 0.9f,
        qualityScore = 0.5f,
        recognitionStatus = status,
        motionStatus = "MOVING",
        direction = "LEFT_TO_RIGHT",
        vehicleClass = "CAR",
        firstSeenAt = at,
        bestFrameAt = at,
        lastSeenAt = at,
        zoneId = "Europe/Istanbul",
        scenePath = "s/$id.jpg",
        sceneSha256 = "x",
        sceneSize = 100,
        vehiclePath = null,
        vehicleSha256 = null,
        vehicleSize = null,
        platePath = null,
        plateSha256 = null,
        plateSize = null,
        modelVersions = "m",
        appVersion = "a",
        userNote = null,
        isStarred = false,
        isProtected = protected,
        createdAt = at,
        updatedAt = at,
    )

    private fun query(
        from: Long? = null,
        to: Long? = null,
        plate: String? = null,
        statuses: Set<RecognitionStatus> = emptySet(),
        protectedOnly: Boolean = false,
    ) = ObservationQueryBuilder.build(ObservationQuery(from, to, plate, statuses, protectedOnly = protectedOnly))

    @Test
    fun `time window query is half-open and newest first`() =
        runTest {
            listOf(entity("a", 100), entity("b", 200), entity("c", 300)).forEach { dao.insert(it) }

            val result = dao.query(query(from = 100, to = 300))

            assertThat(result.map { it.id }).containsExactly("b", "a").inOrder()
        }

    @Test
    fun `partial plate search matches prefix, middle and suffix ignoring spaces`() =
        runTest {
            dao.insert(entity("a", 1, plate = "34 AB 123"))
            dao.insert(entity("b", 2, plate = "06 XY 99"))

            assertThat(dao.query(query(plate = "34 ab")).map { it.id }).containsExactly("a")
            assertThat(dao.query(query(plate = "B12")).map { it.id }).containsExactly("a")
            assertThat(dao.query(query(plate = "…123")).map { it.id }).containsExactly("a")
            assertThat(dao.query(query(plate = "%")).map { it.id }).containsExactly("b", "a")
        }

    @Test
    fun `like wildcards in user input are escaped`() =
        runTest {
            dao.insert(entity("a", 1, plate = "34AB123"))

            assertThat(dao.query(query(plate = "3_A"))).isEmpty()
        }

    @Test
    fun `status filter and protect flag`() =
        runTest {
            dao.insert(entity("a", 1, status = "UNREADABLE"))
            dao.insert(entity("b", 2, status = "HIGH_CONFIDENCE"))
            dao.setProtected(listOf("a"), true, now = 5)

            assertThat(
                dao.query(query(statuses = setOf(RecognitionStatus.UNREADABLE))).map { it.id },
            ).containsExactly("a")
            assertThat(dao.query(query(protectedOnly = true)).map { it.id }).containsExactly("a")
        }

    @Test
    fun `status counts per time window feed the dashboard`() =
        runTest {
            dao.insert(entity("a", 10, status = "UNREADABLE"))
            dao.insert(entity("b", 20, status = "UNREADABLE"))
            dao.insert(entity("c", 30, status = "HIGH_CONFIDENCE"))

            val counts = dao.observeStatusCounts(0, 25).first()

            assertThat(counts.associate { it.status to it.count }).containsExactly("UNREADABLE", 2)
        }

    @Test
    fun `retention rows sum evidence sizes`() =
        runTest {
            dao.insert(entity("a", 1).copy(vehicleSize = 50, plateSize = 5))

            assertThat(dao.retentionRows().single().totalSize).isEqualTo(155)
        }
}
