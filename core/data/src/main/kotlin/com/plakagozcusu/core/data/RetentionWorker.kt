package com.plakagozcusu.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plakagozcusu.core.common.AppLogger
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.storage.RetentionPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Daily retention + cap enforcement + temp/orphan sweep. Protected records are never touched. */
@HiltWorker
class RetentionWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: ObservationRepository,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val s = settings.settings.first()
            val policy = RetentionPolicy(s.retentionDays, s.storageCapMb.toLong() * BYTES_PER_MB)
            val report = repository.runMaintenance(policy)
            val code =
                "done_records_${report.deletedRecords}" +
                    "_tmp_${report.deletedTempFiles}_orphans_${report.deletedOrphans}"
            logger.info(stage = "retention", code = code)
            return Result.success()
        }

        companion object {
            private const val NAME = "retention-daily"
            private const val BYTES_PER_MB = 1024L * 1024

            fun schedule(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
