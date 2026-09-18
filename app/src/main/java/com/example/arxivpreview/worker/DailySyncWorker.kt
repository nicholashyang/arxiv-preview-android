package com.example.arxivpreview.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.R
import kotlinx.coroutines.flow.first

class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ArxivApplication).container
        val preferences = container.preferencesRepository.preferences.first()
        if (preferences.categories.isEmpty()) return Result.success()
        return try {
            val result = container.discoverySync.refresh()
            if (result.newCount > 0 && preferences.notificationsEnabled) notifyNewPapers(result.newCount)
            if (result.pending && runAttemptCount < 2) Result.retry() else Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (error: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun notifyNewPapers(count: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    applicationContext.getString(R.string.notification_channel_description)
            },
        )
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.OPEN_INBOX, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$count new arXiv ${if (count == 1) "paper" else "papers"}")
            .setContentText("Updates from your authors and topics are ready in Inbox.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Access can be revoked between the check and delivery.
        }
    }

    companion object {
        private const val CHANNEL_ID = "daily_updates"
        private const val NOTIFICATION_ID = 1001
    }
}
