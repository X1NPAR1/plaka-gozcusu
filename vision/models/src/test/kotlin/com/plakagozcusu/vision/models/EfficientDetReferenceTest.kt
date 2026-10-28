package com.plakagozcusu.vision.models

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.vision.api.DetectionDecoder
import com.plakagozcusu.vision.api.DetectionTensors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Golden check of the tensor contract: outputs dumped by the Python LiteRT reference run
 * (tools/inspect_tflite.py on a synthetic image) are decoded with the same code the app uses.
 * Verifies shapes, value ranges and box orientation; on-device inference is covered by the
 * instrumented/benchmark checks in Phase 9/10.
 */
class EfficientDetReferenceTest {
    private val outputs =
        Json
            .parseToJsonElement(javaClass.getResource("/efficientdet_lite0_reference.json")!!.readText())
            .jsonObject["outputs"]!!
            .jsonObject

    private fun tensor(name: String) = outputs[name]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()

    @Test
    fun `reference outputs have the measured shapes`() {
        assertThat(tensor("StatefulPartitionedCall:3")).hasLength(100)
        assertThat(tensor("StatefulPartitionedCall:2")).hasLength(25)
        assertThat(tensor("StatefulPartitionedCall:1")).hasLength(25)
        assertThat(tensor("StatefulPartitionedCall:0").single()).isEqualTo(25f)
    }

    @Test
    fun `classes are integral label indices and scores are probabilities`() {
        tensor("StatefulPartitionedCall:2").forEach { assertThat(it % 1f).isEqualTo(0f) }
        tensor(
            "StatefulPartitionedCall:1",
        ).forEach {
            assertThat(it).isIn(
                com.google.common.collect.Range
                    .closed(0f, 1f),
            )
        }
    }

    @Test
    fun `decoder produces valid normalized boxes from real model output`() {
        val all =
            DetectionDecoder.decode(
                DetectionTensors(
                    tensor("StatefulPartitionedCall:3"),
                    tensor("StatefulPartitionedCall:2"),
                    tensor("StatefulPartitionedCall:1"),
                    25,
                ),
                minScore = 0f,
                classMap = (0 until 90).associateWith { com.plakagozcusu.core.model.VehicleClass.UNKNOWN },
            )

        assertThat(all).isNotEmpty()
        all.forEach {
            assertThat(it.box.left).isAtMost(it.box.right)
            assertThat(it.box.top).isAtMost(it.box.bottom)
            assertThat(it.box.right).isAtMost(1f)
        }
    }

    @Test
    fun `synthetic image yields no confident vehicle`() {
        val vehicles =
            DetectionDecoder.decode(
                DetectionTensors(
                    tensor("StatefulPartitionedCall:3"),
                    tensor("StatefulPartitionedCall:2"),
                    tensor("StatefulPartitionedCall:1"),
                    25,
                ),
                minScore = 0.5f,
            )

        assertThat(vehicles).isEmpty()
    }

    @Test
    fun `bundled spec hash matches the documented artifact`() {
        assertThat(EfficientDetVehicleDetector.SPEC.sha256)
            .isEqualTo("2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b")
    }
}
