package com.plakagozcusu.core.storage

/** Minimal view of a record for retention decisions. */
data class RetentionCandidate(
    val id: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val isProtected: Boolean,
)

data class RetentionPolicy(
    val retentionDays: Int,
    val storageCapBytes: Long,
) {
    init {
        require(retentionDays > 0 && storageCapBytes > 0)
    }
}

/**
 * Decides which records to delete: first everything older than the retention period, then the
 * oldest remaining records until total size fits the cap. Protected ("Koru") records are never
 * selected, even if that means the cap cannot be met (the UI then warns about low storage).
 */
object RetentionPlanner {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun plan(
        candidates: List<RetentionCandidate>,
        policy: RetentionPolicy,
        nowMillis: Long,
    ): Set<String> {
        val cutoff = nowMillis - policy.retentionDays * DAY_MS
        val deletable = candidates.filterNot { it.isProtected }.sortedBy { it.createdAtMillis }
        val expired = deletable.filter { it.createdAtMillis < cutoff }.map { it.id }.toMutableSet()

        var total = candidates.filterNot { it.id in expired }.sumOf { it.sizeBytes }
        for (c in deletable) {
            if (total <= policy.storageCapBytes) break
            if (expired.add(c.id)) total -= c.sizeBytes
        }
        return expired
    }
}
