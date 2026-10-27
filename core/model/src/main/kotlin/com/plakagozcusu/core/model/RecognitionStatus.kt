package com.plakagozcusu.core.model

/**
 * Honest recognition outcome of an observation. [HIGH_CONFIDENCE] is a heuristic, never proof;
 * only the user can move a record to [USER_VERIFIED] or [USER_CORRECTED].
 */
enum class RecognitionStatus {
    PENDING,
    HIGH_CONFIDENCE,
    NEEDS_REVIEW,
    UNREADABLE,
    NO_PLATE_VISIBLE,
    FAILED,
    USER_VERIFIED,
    USER_CORRECTED,
    ;

    val isUserConfirmed: Boolean get() = this == USER_VERIFIED || this == USER_CORRECTED

    /** True when a readable plate text is expected to be shown as the record's title. */
    val hasPlateText: Boolean get() = this == HIGH_CONFIDENCE || this == NEEDS_REVIEW || isUserConfirmed
}
