package com.plakagozcusu.feature.monitor.service

import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.vision.pipeline.TrackOutcome

/** Converts monotonic pipeline times to wall-clock UTC millis using one anchor taken at session start. */
data class ClockAnchor(
    val wallMillis: Long,
    val monotonicMillis: Long,
) {
    fun toWall(monotonicMs: Long): Long = wallMillis + (monotonicMs - monotonicMillis)
}

/** Pure mapping from a finalized track to the persisted observation (files are written separately). */
object OutcomeMapper {
    @Suppress("LongParameterList") // A flat value mapping; grouping would only add indirection.
    fun toObservation(
        id: String,
        sessionId: String,
        outcome: TrackOutcome,
        anchor: ClockAnchor,
        zoneId: String,
        modelVersions: String,
        appVersion: String,
        linkedObservationId: String?,
        nowMillis: Long,
    ): Observation {
        val s = outcome.summary
        val r = outcome.recognition
        return Observation(
            id = id,
            sessionId = sessionId,
            trackId = s.trackId,
            plateText = r.plateText,
            originalPlateText = null,
            rawOcrText = r.rawOcrText,
            perCharConfidence = r.perCharConfidence,
            plateConfidence = r.plateConfidence,
            detectionConfidence = s.detectionConfidence,
            qualityScore = r.qualityScore,
            recognitionStatus = r.status,
            motionStatus = s.motion,
            direction = s.direction,
            vehicleClass = s.vehicleClass,
            firstSeenAt = anchor.toWall(s.timing.firstSeenMs),
            bestFrameAt = anchor.toWall(s.timing.bestFrameMs),
            lastSeenAt = anchor.toWall(s.timing.lastSeenMs),
            zoneId = zoneId,
            scene = null,
            vehicleCrop = null,
            plateCrop = null,
            modelVersions = modelVersions,
            appVersion = appVersion,
            userNote = null,
            isStarred = false,
            isProtected = false,
            createdAt = nowMillis,
            updatedAt = nowMillis,
            linkedObservationId = linkedObservationId,
        )
    }
}
