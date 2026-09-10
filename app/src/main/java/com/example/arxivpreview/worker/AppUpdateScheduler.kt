package com.example.arxivpreview.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    const val MANUAL_WORK = "manual-app-update"
    const val AUTOMATIC_WORK = "automatic-app-update"

    fun scheduleAutomaticChecks(context: Context): Operation {
        val manager = WorkManager.getInstance(context)
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(AppUpdateWorker.AUTOMATIC to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        return manager.enqueueUniquePeriodicWork(AUTOMATIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun checkNow(context: Context) = enqueue(context, download = false)
    fun download(context: Context) = enqueue(context, download = true)

    private fun enqueue(context: Context, download: Boolean) {
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setInputData(workDataOf(AppUpdateWorker.DOWNLOAD_ONLY to download))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
