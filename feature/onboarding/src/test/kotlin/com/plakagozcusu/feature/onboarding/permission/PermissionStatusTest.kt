package com.plakagozcusu.feature.onboarding.permission

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionStatusTest {
    @Test
    fun `granted wins regardless of other signals`() {
        assertThat(resolvePermissionStatus(granted = true, shouldShowRationale = false, requestedBefore = true))
            .isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `never requested is not treated as permanently denied`() {
        assertThat(resolvePermissionStatus(granted = false, shouldShowRationale = false, requestedBefore = false))
            .isEqualTo(PermissionStatus.NOT_REQUESTED)
    }

    @Test
    fun `denied once with rationale can be asked again`() {
        assertThat(resolvePermissionStatus(granted = false, shouldShowRationale = true, requestedBefore = true))
            .isEqualTo(PermissionStatus.DENIED)
    }

    @Test
    fun `denied without rationale after a request is permanent`() {
        assertThat(resolvePermissionStatus(granted = false, shouldShowRationale = false, requestedBefore = true))
            .isEqualTo(PermissionStatus.PERMANENTLY_DENIED)
    }
}
