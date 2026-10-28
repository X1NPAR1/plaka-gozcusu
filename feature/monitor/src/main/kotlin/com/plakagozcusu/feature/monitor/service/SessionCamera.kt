package com.plakagozcusu.feature.monitor.service

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import com.plakagozcusu.core.camera.CameraConfig
import com.plakagozcusu.core.camera.CameraController
import com.plakagozcusu.core.camera.FrameAnalyzer
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.feature.monitor.MonitorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Camera binding policy of a monitoring session:
 * - a real CameraX Preview only while the dashboard shows it (smooth, full frame rate), otherwise
 *   analysis + capture only, so monitoring never depends on a visible surface;
 * - the target rotation follows the display, so stills and analysis match how the phone is mounted
 *   (portrait or landscape) and update when it is turned.
 */
internal class SessionCamera(
    private val context: Context,
    private val camera: CameraController,
    private val controller: MonitorController,
) {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var listener: DisplayManager.DisplayListener? = null

    suspend fun bind(
        owner: LifecycleOwner,
        analyzer: FrameAnalyzer,
    ): AppResult<Unit> {
        val result = camera.bind(owner, CameraConfig(withPreview = controller.previewWanted.value), analyzer)
        camera.setTargetRotation(rotation())
        return result
    }

    fun observe(
        scope: CoroutineScope,
        owner: LifecycleOwner,
        analyzer: () -> FrameAnalyzer?,
        afterRebind: () -> Unit,
    ) {
        scope.launch {
            controller.previewWanted.drop(1).collect {
                val a = analyzer() ?: return@collect
                camera.unbind()
                bind(owner, a)
                afterRebind()
            }
        }
        val l =
            object : DisplayManager.DisplayListener {
                override fun onDisplayChanged(displayId: Int) = camera.setTargetRotation(rotation())

                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit
            }
        displays.registerDisplayListener(l, null)
        listener = l
    }

    fun stopObserving() {
        listener?.let(displays::unregisterDisplayListener)
        listener = null
    }

    private fun rotation(): Int = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
}
