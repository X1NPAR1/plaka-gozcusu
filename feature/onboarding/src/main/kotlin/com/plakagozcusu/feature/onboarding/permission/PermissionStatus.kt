package com.plakagozcusu.feature.onboarding.permission

/** Runtime permission status as the UI needs it (Android does not expose "permanently denied" directly). */
enum class PermissionStatus { NOT_REQUESTED, GRANTED, DENIED, PERMANENTLY_DENIED }

/**
 * Derives the status from the three signals Android gives us. After at least one request, a denied
 * permission without a rationale means the system will no longer show the dialog ("don't ask again"
 * or the second denial on Android 11+), so the only path left is the app settings page.
 */
fun resolvePermissionStatus(
    granted: Boolean,
    shouldShowRationale: Boolean,
    requestedBefore: Boolean,
): PermissionStatus =
    when {
        granted -> PermissionStatus.GRANTED
        !requestedBefore -> PermissionStatus.NOT_REQUESTED
        shouldShowRationale -> PermissionStatus.DENIED
        else -> PermissionStatus.PERMANENTLY_DENIED
    }
