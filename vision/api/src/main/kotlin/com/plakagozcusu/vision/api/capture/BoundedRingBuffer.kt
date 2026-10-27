package com.plakagozcusu.vision.api.capture

/**
 * Fixed-capacity buffer of the most recent items (pre-event context frames). Bounded both by count
 * and by total [weightOf] (e.g. bytes) so a burst of large frames can never exhaust memory.
 * Evicted items are handed to [onEvict] so pooled buffers can be returned. Not thread-safe: owned
 * by the single analysis thread.
 */
class BoundedRingBuffer<T>(
    private val maxItems: Int,
    private val maxWeight: Long,
    private val weightOf: (T) -> Long,
    private val onEvict: (T) -> Unit = {},
) {
    private val items = ArrayDeque<T>()
    var totalWeight: Long = 0
        private set

    val size: Int get() = items.size

    init {
        require(maxItems > 0 && maxWeight > 0)
    }

    /** Adds [item]; returns false (and evicts it immediately) if it alone exceeds [maxWeight]. */
    fun add(item: T): Boolean {
        val weight = weightOf(item)
        if (weight > maxWeight) {
            onEvict(item)
            return false
        }
        items.addLast(item)
        totalWeight += weight
        while (items.size > maxItems || totalWeight > maxWeight) evictOldest()
        return true
    }

    fun snapshot(): List<T> = items.toList()

    fun clear() {
        while (items.isNotEmpty()) evictOldest()
    }

    private fun evictOldest() {
        val removed = items.removeFirst()
        totalWeight -= weightOf(removed)
        onEvict(removed)
    }
}
