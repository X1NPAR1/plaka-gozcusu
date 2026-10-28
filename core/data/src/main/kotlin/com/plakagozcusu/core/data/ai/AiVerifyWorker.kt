package com.plakagozcusu.core.data.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.plakagozcusu.core.common.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Asks the optional AI provider about one observation's plate; queued only when the user enabled it. */
@HiltWorker
class AiVerifyWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val ai: AiService,
        private val logger: AppLogger,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = inputData.getString(KEY_ID) ?: return Result.failure()
            return when (val r = ai.check(id, requireEnabled = true)) {
                is AiCheckOutcome.Done -> {
                    Result.success().also { logger.info(stage = "ai", code = "verified") }
                }

                is AiCheckOutcome.Failed -> {
                    if (r.code == HTTP_TOO_MANY || r.code < 0 || r.code >= HTTP_SERVER) {
                        logger.warn(stage = "ai", code = "transient_${r.code}")
                        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                    } else {
                        logger.warn(stage = "ai", code = "permanent_${r.code}")
                        Result.failure()
                    }
                }

                else -> {
                    Result.success()
                }
            }
        }

        companion object {
            private const val KEY_ID = "observation_id"
            private const val MAX_ATTEMPTS = 5
            private const val HTTP_TOO_MANY = 429
            private const val HTTP_SERVER = 500
            private const val BACKOFF_SECONDS = 30L

            fun enqueue(
                context: Context,
                observationId: String,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<AiVerifyWorker>()
                        .setInputData(workDataOf(KEY_ID to observationId))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork("ai-$observationId", ExistingWorkPolicy.KEEP, request)
            }
        }
    }
