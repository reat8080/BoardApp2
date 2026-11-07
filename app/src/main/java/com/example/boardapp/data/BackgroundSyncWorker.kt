package com.example.boardapp.data

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackgroundSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.itemDao()
        val syncManager = SyncManager(applicationContext, "https://omidanit.ca/boardapp")

        // دریافت تغییرات جدید از هاست
        syncManager.pullData(dao)
        return Result.success()
    }

    companion object {
        fun scheduleDailySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val dailySyncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyBoardSync",
                ExistingPeriodicWorkPolicy.KEEP,
                dailySyncRequest
            )
        }

        fun triggerInitialSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeSync = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeSync)
        }
    }
}
