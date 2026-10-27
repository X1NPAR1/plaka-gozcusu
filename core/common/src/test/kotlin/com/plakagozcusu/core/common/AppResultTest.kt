package com.plakagozcusu.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {
    @Test
    fun `map transforms success value`() {
        val result: AppResult<Int> = AppResult.Success(2)

        assertThat(result.map { it * 3 }).isEqualTo(AppResult.Success(6))
    }

    @Test
    fun `map keeps failure untouched`() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.StorageFull())

        assertThat(failure.map { it * 3 }).isSameInstanceAs(failure)
        assertThat(failure.getOrNull()).isNull()
    }
}
