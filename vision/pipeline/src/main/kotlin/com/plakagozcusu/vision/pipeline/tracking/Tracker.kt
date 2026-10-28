package com.plakagozcusu.vision.pipeline.tracking

import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.VehicleDetection
import com.plakagozcusu.vision.api.geometry.RectF

enum class TrackState { TENTATIVE, CONFIRMED, LOST, EXPIRED }

/** Bounded box history entry; time in monotonic millis. */
data class TrackSample(
    val timeMs: Long,
    val box: RectF,
    val score: Float,
)

class Track internal constructor(
    val id: Long,
    first: TrackSample,
    var vehicleClass: VehicleClass,
) {
    var state: TrackState = TrackState.TENTATIVE
        internal set
    var hits: Int = 1
        internal set
    var misses: Int = 0
        internal set
    val firstSeenMs: Long = first.timeMs
    var lastSeenMs: Long = first.timeMs
        internal set
    private val history = ArrayDeque<TrackSample>().apply { add(first) }
    val samples: List<TrackSample> get() = history.toList()
    val lastBox: RectF get() = history.last().box

    internal fun add(
        sample: TrackSample,
        maxHistory: Int,
    ) {
        history.addLast(sample)
        while (history.size > maxHistory) history.removeFirst()
        lastSeenMs = sample.timeMs
    }

    /** Constant-velocity prediction of the box at [timeMs] from the last two samples. */
    fun predict(timeMs: Long): RectF {
        if (history.size < 2) return lastBox
        val a = history[history.size - 2]
        val b = history.last()
        val dt = (b.timeMs - a.timeMs).coerceAtLeast(1)
        val k = (timeMs - b.timeMs).toFloat() / dt
        return RectF(
            b.box.left + (b.box.left - a.box.left) * k,
            b.box.top + (b.box.top - a.box.top) * k,
            b.box.right + (b.box.right - a.box.right) * k,
            b.box.bottom + (b.box.bottom - a.box.bottom) * k,
        )
    }
}

data class TrackerConfig(
    val minIou: Float = 0.2f,
    val confirmHits: Int = 3,
    /** A confirmed track survives this long without detections (occlusion, detector dropouts). */
    val maxLostMs: Long = 1_500,
    val maxHistory: Int = 60,
    val maxTracks: Int = 32,
)

/**
 * SORT-style tracker: greedy IoU matching against constant-velocity predictions. Plate text is
 * never used for identity. Deterministic for a given input sequence (replay tests).
 */
class Tracker(
    private val config: TrackerConfig = TrackerConfig(),
) {
    private var nextId = 1L
    private val tracks = mutableListOf<Track>()

    val active: List<Track> get() = tracks.filter { it.state != TrackState.EXPIRED }

    /** Feeds one frame of detections; returns tracks that expired during this update. */
    fun update(
        timeMs: Long,
        detections: List<VehicleDetection>,
    ): List<Track> {
        val live = tracks.filter { it.state != TrackState.EXPIRED }
        val unmatched = detections.sortedByDescending { it.score }.toMutableList()
        val matched = match(timeMs, live, unmatched)
        val expired = ageUnmatched(timeMs, live.filter { it.id !in matched })
        spawn(timeMs, unmatched)
        tracks.removeAll { it.state == TrackState.EXPIRED }
        return expired
    }

    /** Greedy best-IoU assignment; removes used detections from [unmatched]; returns matched ids. */
    private fun match(
        timeMs: Long,
        live: List<Track>,
        unmatched: MutableList<VehicleDetection>,
    ): Set<Long> {
        val pairs =
            live
                .flatMap { t -> unmatched.map { d -> Triple(t, d, t.predict(timeMs).iou(d.box)) } }
                .filter { it.third >= config.minIou }
                .sortedByDescending { it.third }
        val matched = mutableSetOf<Long>()
        for ((track, det, _) in pairs) {
            if (track.id in matched || det !in unmatched) continue
            matched += track.id
            unmatched -= det
            absorb(track, det, timeMs)
        }
        return matched
    }

    private fun absorb(
        track: Track,
        det: VehicleDetection,
        timeMs: Long,
    ) {
        val bestSoFar = track.samples.maxOf { it.score }
        track.add(TrackSample(timeMs, det.box, det.score), config.maxHistory)
        track.hits++
        track.misses = 0
        if (det.score > bestSoFar) track.vehicleClass = det.vehicleClass
        if (track.hits >= config.confirmHits) track.state = TrackState.CONFIRMED
    }

    private fun ageUnmatched(
        timeMs: Long,
        unmatchedTracks: List<Track>,
    ): List<Track> {
        val expired = mutableListOf<Track>()
        for (t in unmatchedTracks) {
            t.misses++
            val expire = t.state == TrackState.TENTATIVE || timeMs - t.lastSeenMs > config.maxLostMs
            t.state = if (expire) TrackState.EXPIRED else TrackState.LOST
            if (expire) expired += t
        }
        return expired
    }

    private fun spawn(
        timeMs: Long,
        unmatched: List<VehicleDetection>,
    ) {
        for (d in unmatched) {
            if (tracks.count { it.state != TrackState.EXPIRED } >= config.maxTracks) break
            tracks += Track(nextId++, TrackSample(timeMs, d.box, d.score), d.vehicleClass)
        }
    }

    /** Expires everything (session end) and returns the tracks that were still alive. */
    fun flush(): List<Track> = tracks.filter { it.state != TrackState.EXPIRED }.also { tracks.clear() }
}
