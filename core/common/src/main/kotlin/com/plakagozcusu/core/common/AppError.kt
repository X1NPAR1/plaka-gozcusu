package com.plakagozcusu.core.common

/**
 * Expected, user-explainable failure categories. UI maps each to a Turkish, actionable message;
 * [cause] is kept for local diagnostics only and never shown to the user.
 */
sealed interface AppError {
    val cause: Throwable?

    data class CameraUnavailable(
        override val cause: Throwable? = null,
    ) : AppError

    data class CameraInUseByAnotherApp(
        override val cause: Throwable? = null,
    ) : AppError

    data class PermissionDenied(
        val permanently: Boolean,
    ) : AppError {
        override val cause: Throwable? = null
    }

    data class ModelUnavailable(
        val modelName: String,
        override val cause: Throwable? = null,
    ) : AppError

    data class StorageFull(
        override val cause: Throwable? = null,
    ) : AppError

    data class Io(
        override val cause: Throwable? = null,
    ) : AppError

    data class Unexpected(
        override val cause: Throwable? = null,
    ) : AppError
}
