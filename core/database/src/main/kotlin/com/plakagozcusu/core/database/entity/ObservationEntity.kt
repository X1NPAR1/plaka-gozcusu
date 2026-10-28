package com.plakagozcusu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Flat row for an observation. Enums are stored by name (stable across reordering). Evidence files
 * are stored as path + sha256 + size triples. [plateSearch] is the plate text with separators
 * removed and uppercased, so "34 AB 123" matches queries "34AB", "AB1" or "123".
 */
@Entity(
    tableName = "observations",
    indices = [
        Index("best_frame_at"),
        Index("plate_search"),
        Index("recognition_status"),
        Index("is_protected"),
        Index("session_id"),
    ],
)
data class ObservationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "plate_text") val plateText: String?,
    @ColumnInfo(name = "original_plate_text") val originalPlateText: String?,
    @ColumnInfo(name = "plate_search") val plateSearch: String?,
    @ColumnInfo(name = "raw_ocr_text") val rawOcrText: String?,
    @ColumnInfo(name = "per_char_confidence") val perCharConfidence: String?,
    @ColumnInfo(name = "plate_confidence") val plateConfidence: Float,
    @ColumnInfo(name = "detection_confidence") val detectionConfidence: Float,
    @ColumnInfo(name = "quality_score") val qualityScore: Float,
    @ColumnInfo(name = "recognition_status") val recognitionStatus: String,
    @ColumnInfo(name = "motion_status") val motionStatus: String,
    val direction: String,
    @ColumnInfo(name = "vehicle_class") val vehicleClass: String,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "best_frame_at") val bestFrameAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "scene_path") val scenePath: String?,
    @ColumnInfo(name = "scene_sha256") val sceneSha256: String?,
    @ColumnInfo(name = "scene_size") val sceneSize: Long?,
    @ColumnInfo(name = "vehicle_path") val vehiclePath: String?,
    @ColumnInfo(name = "vehicle_sha256") val vehicleSha256: String?,
    @ColumnInfo(name = "vehicle_size") val vehicleSize: Long?,
    @ColumnInfo(name = "plate_path") val platePath: String?,
    @ColumnInfo(name = "plate_sha256") val plateSha256: String?,
    @ColumnInfo(name = "plate_size") val plateSize: Long?,
    @ColumnInfo(name = "model_versions") val modelVersions: String,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "user_note") val userNote: String?,
    @ColumnInfo(name = "is_starred") val isStarred: Boolean,
    @ColumnInfo(name = "is_protected") val isProtected: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "linked_observation_id") val linkedObservationId: String? = null,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "device_info") val deviceInfo: String,
    @ColumnInfo(name = "settings_snapshot") val settingsSnapshot: String,
    @ColumnInfo(name = "frames_analyzed") val framesAnalyzed: Long,
    @ColumnInfo(name = "frames_dropped") val framesDropped: Long,
    val vehicles: Int,
    @ColumnInfo(name = "plates_read") val platesRead: Int,
    val uncertain: Int,
    val unreadable: Int,
    @ColumnInfo(name = "end_reason") val endReason: String?,
)

/** Privacy-safe diagnostics: stage/code/duration only, never plate text or images. */
@Entity(tableName = "diagnostic_events", indices = [Index("timestamp")])
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val stage: String,
    val code: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
)
