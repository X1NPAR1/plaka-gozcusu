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
import com.plakagozcusu.core.data.ObservationRepository
import com.plakagozcusu.core.datastore.SecretStore
import com.plakagozcusu.core.datastore.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Asks the optional AI provider about one observation's plate; queued only when the user enabled it. */
@HiltWorker
class AiVerifyWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: ObservationRepository,
        private val settings: SettingsRepository,
        private val secrets: SecretStore,
        private val logger: AppLogger,
    ) : CoroutineWorker(context, params) {
        /** Everything needed for one request, or null when there is nothing to do. */
        private class Job(
            val id: String,
            val verifier: GeminiPlateVerifier,
            val image: ByteArray,
            val candidates: List<String>,
        )

        private suspend fun prepare(): Job? {
            val id = inputData.getString(KEY_ID)
            val s = settings.settings.first()
            val key = secrets.aiKey()
            val o = id?.takeIf { s.aiEnabled && key != null }?.let { repository.get(it) }?.takeIf { !it.recognitionStatus.isUserConfirmed }
            val crop = o?.let { it.plateCrop ?: it.vehicleCrop }?.let(repository::resolveFile)?.takeIf { it.isFile }
            return if (o != null && crop != null && key != null) {
                Job(o.id, GeminiPlateVerifier(key, s.aiModel), crop.readBytes(), listOfNotNull(o.plateText, o.rawOcrText).distinct())
            } else {
                null
            }
        }

        override suspend fun doWork(): Result {
            // Disabled, no key, record gone, user already decided or no image: nothing to do.
            val job = prepare() ?: return Result.success()
            val id = job.id
            return when (val r = job.verifier.verify(job.image, job.candidates)) {
                is AiResult.Read -> {
                    repository.applyAiOpinion(id, r.plate, GeminiPlateVerifier.PROVIDER)
                    logger.info(stage = "ai", code = "verified")
                    Result.success()
                }

                is AiResult.Transient -> {
                    logger.warn(stage = "ai", code = "transient_${r.code}")
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }

                is AiResult.Permanent -> {
                    logger.warn(stage = "ai", code = "permanent_${r.code}")
                    Result.failure()
                }
            }
        }

        companion object {
            private const val KEY_ID = "observation_id"
            private const val MAX_ATTEMPTS = 5
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
