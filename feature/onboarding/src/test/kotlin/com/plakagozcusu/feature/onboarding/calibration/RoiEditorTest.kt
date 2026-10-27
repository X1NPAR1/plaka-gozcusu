package com.plakagozcusu.feature.onboarding.calibration

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.vision.api.geometry.PointF
import org.junit.Test

class RoiEditorTest {
    @Test
    fun `region becomes available at three points`() {
        val two = RoiEditor().add(PointF(0.1f, 0.5f)).add(PointF(0.9f, 0.5f))

        assertThat(two.toRegionOrNull()).isNull()
        assertThat(two.add(PointF(0.5f, 0.9f)).toRegionOrNull()).isNotNull()
    }

    @Test
    fun `taps outside the frame are clamped`() {
        val editor = RoiEditor().add(PointF(-0.2f, 1.4f))

        assertThat(editor.points.single()).isEqualTo(PointF(0f, 1f))
    }

    @Test
    fun `undo removes the last point and is safe when empty`() {
        val editor = RoiEditor().add(PointF(0.1f, 0.1f)).add(PointF(0.2f, 0.2f)).undo()

        assertThat(editor.points).containsExactly(PointF(0.1f, 0.1f))
        assertThat(RoiEditor().undo().points).isEmpty()
    }

    @Test
    fun `point count is capped`() {
        var editor = RoiEditor()
        repeat(RoiEditor.MAX_POINTS + 3) { editor = editor.add(PointF(0.5f, 0.5f)) }

        assertThat(editor.points).hasSize(RoiEditor.MAX_POINTS)
        assertThat(editor.canAdd).isFalse()
    }
}
