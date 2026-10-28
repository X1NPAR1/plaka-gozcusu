package com.plakagozcusu.core.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetentionPlannerTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun c(
        id: String,
        ageDays: Int,
        size: Long = 10,
        protected: Boolean = false,
    ) = RetentionCandidate(id, now - ageDays * day, size, protected)

    @Test
    fun `records older than the retention period are deleted`() {
        val plan = RetentionPlanner.plan(listOf(c("old", 31), c("new", 5)), RetentionPolicy(30, 1_000), now)

        assertThat(plan).containsExactly("old")
    }

    @Test
    fun `protected records are never deleted even when expired or over cap`() {
        val plan =
            RetentionPlanner.plan(
                listOf(c("p", 90, size = 500, protected = true), c("x", 1, size = 500)),
                RetentionPolicy(30, 100),
                now,
            )

        assertThat(plan).containsExactly("x")
    }

    @Test
    fun `oldest unprotected records go first until under the cap`() {
        val records = listOf(c("a", 3, 40), c("b", 2, 40), c("c", 1, 40))

        val plan = RetentionPlanner.plan(records, RetentionPolicy(30, 50), now)

        assertThat(plan).containsExactly("a", "b")
    }

    @Test
    fun `nothing is deleted when within limits`() {
        assertThat(RetentionPlanner.plan(listOf(c("a", 1)), RetentionPolicy(30, 1_000), now)).isEmpty()
    }
}
