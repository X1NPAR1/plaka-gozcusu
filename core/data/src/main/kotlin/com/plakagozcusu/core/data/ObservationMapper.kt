package com.plakagozcusu.core.data

import com.plakagozcusu.core.database.dao.normalizePlateForSearch
import com.plakagozcusu.core.database.entity.ObservationEntity
import com.plakagozcusu.core.model.AiOpinion
import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.TravelDirection
import com.plakagozcusu.core.model.VehicleClass

internal fun Observation.toEntity() =
    ObservationEntity(
        id = id,
        sessionId = sessionId,
        trackId = trackId,
        plateText = plateText,
        originalPlateText = originalPlateText,
        plateSearch = normalizePlateForSearch(plateText),
        rawOcrText = rawOcrText,
        perCharConfidence = perCharConfidence?.joinToString(","),
        plateConfidence = plateConfidence,
        detectionConfidence = detectionConfidence,
        qualityScore = qualityScore,
        recognitionStatus = recognitionStatus.name,
        motionStatus = motionStatus.name,
        direction = direction.name,
        vehicleClass = vehicleClass.name,
        firstSeenAt = firstSeenAt,
        bestFrameAt = bestFrameAt,
        lastSeenAt = lastSeenAt,
        zoneId = zoneId,
        scenePath = scene?.relativePath,
        sceneSha256 = scene?.sha256,
        sceneSize = scene?.sizeBytes,
        vehiclePath = vehicleCrop?.relativePath,
        vehicleSha256 = vehicleCrop?.sha256,
        vehicleSize = vehicleCrop?.sizeBytes,
        platePath = plateCrop?.relativePath,
        plateSha256 = plateCrop?.sha256,
        plateSize = plateCrop?.sizeBytes,
        modelVersions = modelVersions,
        appVersion = appVersion,
        userNote = userNote,
        isStarred = isStarred,
        isProtected = isProtected,
        createdAt = createdAt,
        updatedAt = updatedAt,
        linkedObservationId = linkedObservationId,
        aiPlateText = ai?.plateText,
        aiProvider = ai?.provider,
        aiCheckedAt = ai?.checkedAt,
        aiAgrees = ai?.agreesWithReading,
    )

internal fun ObservationEntity.toModel() =
    Observation(
        id = id,
        sessionId = sessionId,
        trackId = trackId,
        plateText = plateText,
        originalPlateText = originalPlateText,
        rawOcrText = rawOcrText,
        perCharConfidence = perCharConfidence?.split(',')?.mapNotNull { it.toFloatOrNull() },
        plateConfidence = plateConfidence,
        detectionConfidence = detectionConfidence,
        qualityScore = qualityScore,
        recognitionStatus = enumOr(recognitionStatus, RecognitionStatus.FAILED),
        motionStatus = enumOr(motionStatus, MotionStatus.UNKNOWN),
        direction = enumOr(direction, TravelDirection.UNKNOWN),
        vehicleClass = enumOr(vehicleClass, VehicleClass.UNKNOWN),
        firstSeenAt = firstSeenAt,
        bestFrameAt = bestFrameAt,
        lastSeenAt = lastSeenAt,
        zoneId = zoneId,
        scene = evidence(scenePath, sceneSha256, sceneSize),
        vehicleCrop = evidence(vehiclePath, vehicleSha256, vehicleSize),
        plateCrop = evidence(platePath, plateSha256, plateSize),
        modelVersions = modelVersions,
        appVersion = appVersion,
        userNote = userNote,
        isStarred = isStarred,
        isProtected = isProtected,
        createdAt = createdAt,
        updatedAt = updatedAt,
        linkedObservationId = linkedObservationId,
        ai =
            aiProvider?.let { provider ->
                aiCheckedAt?.let { at -> AiOpinion(aiPlateText, provider, at, aiAgrees == true) }
            },
    )

internal val ObservationEntity.evidencePaths: List<String>
    get() = listOfNotNull(scenePath, vehiclePath, platePath)

private fun evidence(
    path: String?,
    sha: String?,
    size: Long?,
) = if (path != null && sha != null && size != null) EvidenceFile(path, sha, size) else null

private inline fun <reified E : Enum<E>> enumOr(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default
