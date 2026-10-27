package com.plakagozcusu.vision.api.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameTransformTest {
    private val tol = 1e-4f

    private fun assertPoint(
        actual: PointF,
        x: Float,
        y: Float,
    ) {
        assertThat(actual.x).isWithin(tol).of(x)
        assertThat(actual.y).isWithin(tol).of(y)
    }

    @Test
    fun `rotation 0 maps corners directly`() {
        val t = FrameTransform(Size(640, 480), rotationDegrees = 0)

        assertPoint(t.bufferToScene(PointF(0f, 0f)), 0f, 0f)
        assertPoint(t.bufferToScene(PointF(640f, 480f)), 1f, 1f)
        assertThat(t.uprightSize).isEqualTo(Size(640, 480))
    }

    @Test
    fun `rotation 90 maps buffer top-left to scene top-right`() {
        // Typical portrait phone: landscape sensor buffer rotated 90 degrees clockwise.
        val t = FrameTransform(Size(640, 480), rotationDegrees = 90)

        assertPoint(t.bufferToScene(PointF(0f, 0f)), 1f, 0f)
        assertPoint(t.bufferToScene(PointF(0f, 480f)), 0f, 0f)
        assertThat(t.uprightSize).isEqualTo(Size(480, 640))
    }

    @Test
    fun `rotation 180 and 270 map corners correctly`() {
        val t180 = FrameTransform(Size(640, 480), rotationDegrees = 180)
        val t270 = FrameTransform(Size(640, 480), rotationDegrees = 270)

        assertPoint(t180.bufferToScene(PointF(0f, 0f)), 1f, 1f)
        assertPoint(t270.bufferToScene(PointF(0f, 0f)), 0f, 1f)
    }

    @Test
    fun `scene to buffer is the inverse for every rotation and crop`() {
        val crop = RectF(40f, 30f, 600f, 450f)
        listOf(0, 90, 180, 270, -90, 450).forEach { rotation ->
            val t = FrameTransform(Size(640, 480), crop, rotation)
            listOf(PointF(40f, 30f), PointF(321.5f, 99f), PointF(600f, 450f)).forEach { p ->
                val back = t.sceneToBuffer(t.bufferToScene(p))
                assertPoint(back, p.x, p.y)
            }
        }
    }

    @Test
    fun `box found in analysis frame maps onto the same region of a high-res still`() {
        val analysis = FrameTransform(Size(640, 480), rotationDegrees = 90)
        val still = FrameTransform(Size(4000, 3000), rotationDegrees = 90)
        val boxInAnalysis = RectF(100f, 200f, 300f, 260f)

        val scene = analysis.bufferToScene(boxInAnalysis)
        val boxInStill = still.sceneToBuffer(scene)

        assertThat(boxInStill.left).isWithin(0.01f).of(100f * 4000f / 640f)
        assertThat(boxInStill.top).isWithin(0.01f).of(200f * 3000f / 480f)
        assertThat(boxInStill.right).isWithin(0.01f).of(300f * 4000f / 640f)
        assertThat(boxInStill.bottom).isWithin(0.01f).of(260f * 3000f / 480f)
    }

    @Test
    fun `crop rect offsets are honoured`() {
        val t = FrameTransform(Size(640, 480), RectF(80f, 0f, 560f, 480f), rotationDegrees = 0)

        assertPoint(t.bufferToScene(PointF(80f, 0f)), 0f, 0f)
        assertPoint(t.bufferToScene(PointF(320f, 240f)), 0.5f, 0.5f)
        assertThat(t.uprightSize).isEqualTo(Size(480, 480))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non right-angle rotation is rejected`() {
        FrameTransform(Size(640, 480), rotationDegrees = 45)
    }

    @Test
    fun `iou of identical and disjoint rects`() {
        val a = RectF(0f, 0f, 10f, 10f)

        assertThat(a.iou(a)).isWithin(tol).of(1f)
        assertThat(a.iou(RectF(20f, 20f, 30f, 30f))).isEqualTo(0f)
        assertThat(a.iou(RectF(5f, 0f, 15f, 10f))).isWithin(tol).of(50f / 150f)
    }

    @Test
    fun `edge contact detects possibly cut-off objects`() {
        val size = Size(100, 100)

        assertThat(RectF(0f, 10f, 20f, 20f).touchesEdgeOf(size, 1f)).isTrue()
        assertThat(RectF(10f, 10f, 20f, 20f).touchesEdgeOf(size, 1f)).isFalse()
    }
}
