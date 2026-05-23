package com.theblankstate.libri.recommendation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theblankstate.libri.data.UserPreferencesRepository
import java.util.concurrent.TimeUnit

class RecommendationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            val store = RecommendationStore(applicationContext)
            val preferences = UserPreferencesRepository(applicationContext)
            if (!preferences.isRecommendationSyncEnabled()) return Result.success()
            val signals = store.loadSignals(preferences)
            val profile = store.profileSnapshot(signals)
            val sections = store.latestHomeSnapshotForSync()
            val events = store.latestEvents()
            if (sections.isNotEmpty() || events.isNotEmpty()) {
                RecommendationSyncRepository(applicationContext)
                    .syncHomeRecommendations(profile, sections, events)
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "recommendation_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecommendationSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
