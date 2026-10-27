package com.plakagozcusu.vision.api.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoundedRingBufferTest {
    private val evicted = mutableListOf<Int>()
    private val buffer =
        BoundedRingBuffer<Int>(maxItems = 3, maxWeight = 100, weightOf = { it.toLong() }, onEvict = {
            evicted +=
                it
        })

    @Test
    fun `keeps only the most recent items by count`() {
        listOf(1, 2, 3, 4, 5).forEach { buffer.add(it) }

        assertThat(buffer.snapshot()).containsExactly(3, 4, 5).inOrder()
        assertThat(evicted).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `evicts oldest when total weight is exceeded`() {
        buffer.add(40)
        buffer.add(40)
        buffer.add(30)

        assertThat(buffer.snapshot()).containsExactly(40, 30).inOrder()
        assertThat(buffer.totalWeight).isEqualTo(70)
    }

    @Test
    fun `an oversized item is rejected and released`() {
        assertThat(buffer.add(500)).isFalse()
        assertThat(buffer.size).isEqualTo(0)
        assertThat(evicted).containsExactly(500)
    }

    @Test
    fun `clear releases every item`() {
        listOf(1, 2, 3).forEach { buffer.add(it) }
        buffer.clear()

        assertThat(buffer.size).isEqualTo(0)
        assertThat(buffer.totalWeight).isEqualTo(0)
        assertThat(evicted).containsExactly(1, 2, 3)
    }
}
