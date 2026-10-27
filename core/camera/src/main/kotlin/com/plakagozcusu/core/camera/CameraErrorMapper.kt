package com.plakagozcusu.core.camera

import com.plakagozcusu.core.common.AppError
import androidx.camera.core.CameraState as XCameraState

/** Maps CameraX camera-state errors to app states the UI can explain in Turkish. */
internal object CameraErrorMapper {
    fun map(error: XCameraState.StateError): CameraState =
        when (error.code) {
            XCameraState.ERROR_CAMERA_IN_USE,
            XCameraState.ERROR_MAX_CAMERAS_IN_USE,
            -> CameraState.Interrupted(AppError.CameraInUseByAnotherApp(error.cause))

            XCameraState.ERROR_OTHER_RECOVERABLE_ERROR,
            XCameraState.ERROR_STREAM_CONFIG,
            -> CameraState.Interrupted(AppError.CameraUnavailable(error.cause))

            XCameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED,
            XCameraState.ERROR_CAMERA_DISABLED,
            XCameraState.ERROR_CAMERA_FATAL_ERROR,
            XCameraState.ERROR_CAMERA_REMOVED,
            -> CameraState.Failed(AppError.CameraUnavailable(error.cause))

            else -> CameraState.Failed(AppError.Unexpected(error.cause))
        }
}
