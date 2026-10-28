package com.plakagozcusu.core.model

/**
 * One vehicle pass. Timestamps are UTC epoch millis plus the zone id at capture so DST/time-zone
 * changes never shift evidence. Plate text fields are null unless a reading exists; [rawOcrText]
 * always keeps what OCR produced, [plateText] is the normalized/consensus value (or the user's
 * correction, with the original preserved in [originalPlateText]).
 */
data class Observation(
    val id: String,
    val sessionId: String,
    val trackId: Long,
    val plateText: String?,
    val originalPlateText: String?,
    val rawOcrText: String?,
    val perCharConfidence: List<Float>?,
    val plateConfidence: Float,
    val detectionConfidence: Float,
    val qualityScore: Float,
    val recognitionStatus: RecognitionStatus,
    val motionStatus: MotionStatus,
    val direction: TravelDirection,
    val vehicleClass: VehicleClass,
    val firstSeenAt: Long,
    val bestFrameAt: Long,
    val lastSeenAt: Long,
    val zoneId: String,
    val scene: EvidenceFile?,
    val vehicleCrop: EvidenceFile?,
    val plateCrop: EvidenceFile?,
    val modelVersions: String,
    val appVersion: String,
    val userNote: String?,
    val isStarred: Boolean,
    val isProtected: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A stored evidence file: path relative to the evidence root, its SHA-256 (hex) and size. */
data class EvidenceFile(
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
)

data class MonitoringSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val deviceInfo: String,
    val settingsSnapshot: String,
    val stats: SessionStats,
    val endReason: String?,
)

data class SessionStats(
    val framesAnalyzed: Long = 0,
    val framesDropped: Long = 0,
    val vehicles: Int = 0,
    val platesRead: Int = 0,
    val uncertain: Int = 0,
    val unreadable: Int = 0,
)

/** Filter for timeline/incident queries; all fields optional. Times are UTC epoch millis. */
data class ObservationQuery(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val plateFragment: String? = null,
    val statuses: Set<RecognitionStatus> = emptySet(),
    val directions: Set<TravelDirection> = emptySet(),
    val vehicleClasses: Set<VehicleClass> = emptySet(),
    val starredOnly: Boolean = false,
    val protectedOnly: Boolean = false,
    val newestFirst: Boolean = true,
)
