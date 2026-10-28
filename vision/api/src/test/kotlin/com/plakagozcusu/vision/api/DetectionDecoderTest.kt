package com.plakagozcusu.vision.api

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.geometry.RectF
import org.junit.Test

class DetectionDecoderTest {
    @Test
    fun `decodes ymin-xmin-ymax-xmax into left-top-right-bottom and maps COCO classes`() {
        val boxes = floatArrayOf(0.1f, 0.2f, 0.5f, 0.6f, 0f, 0f, 1f, 1f)
        val result =
            DetectionDecoder.decode(
                boxes,
                floatArrayOf(2f, 7f),
                floatArrayOf(0.9f, 0.8f),
                count = 2,
                minScore = 0.5f,
            )

        assertThat(result)
            .containsExactly(
                VehicleDetection(RectF(0.2f, 0.1f, 0.6f, 0.5f), VehicleClass.CAR, 0.9f),
                VehicleDetection(RectF(0f, 0f, 1f, 1f), VehicleClass.TRUCK, 0.8f),
            ).inOrder()
    }

    @Test
    fun `people, bicycles and low scores are dropped`() {
        val boxes = FloatArray(12) { if (it % 4 >= 2) 0.5f else 0.1f }
        val result =
            DetectionDecoder.decode(
                boxes,
                floatArrayOf(0f, 1f, 2f),
                floatArrayOf(0.99f, 0.99f, 0.3f),
                count = 3,
                minScore = 0.5f,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `count never reads past the arrays and degenerate boxes are skipped`() {
        val result =
            DetectionDecoder.decode(
                floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
                floatArrayOf(2f),
                floatArrayOf(0.9f),
                count = 25,
                minScore = 0f,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `overlap suppression keeps the higher score of a car-truck double label`() {
        val car = VehicleDetection(RectF(0.1f, 0.1f, 0.5f, 0.5f), VehicleClass.CAR, 0.7f)
        val truck = VehicleDetection(RectF(0.12f, 0.1f, 0.5f, 0.52f), VehicleClass.TRUCK, 0.6f)
        val other = VehicleDetection(RectF(0.6f, 0.6f, 0.9f, 0.9f), VehicleClass.CAR, 0.5f)

        assertThat(DetectionDecoder.suppressOverlaps(listOf(truck, car, other), 0.6f)).containsExactly(car, other)
    }

    @Test
    fun `image crop copies the right pixels`() {
        val img = ImageBuffer(4, 3, IntArray(12) { it })

        val crop = img.crop(RectF(1f, 1f, 3f, 3f))

        assertThat(crop.width).isEqualTo(2)
        assertThat(crop.argb.toList()).containsExactly(5, 6, 9, 10).inOrder()
    }
}
