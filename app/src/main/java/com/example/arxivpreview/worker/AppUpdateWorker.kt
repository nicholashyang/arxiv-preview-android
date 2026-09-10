package com.example.arxivpreview.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.data.update.UpdateAction
import com.example.arxivpreview.data.update.UpdateResult
import com.example.arxivpreview.R
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class AppUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ArxivApplication).container
        val automatic = inputData.getBoolean(AUTOMATIC, false)
        return try {
            val repository = container.appUpdateRepository
            val result = when (UpdateAction.fromWorkerInput(automatic, inputData.getBoolean(DOWNLOAD_ONLY, false))) {
                UpdateAction.CHECK -> repository.checkForUpdate()
                UpdateAction.DOWNLOAD -> repository.downloadUpdate()
            }
            val release = when (result) {
                is UpdateResult.Available -> if (automatic) result.release else null
                is UpdateResult.Downloaded -> result.release
                UpdateResult.NoChange -> null
            }
            if (release != null) {
                val ready = repository.state.first().readyToInstall
                if (repository.shouldNotify(release, ready) && notifyUpdate(release.version, ready)) {
                    repository.markNotified(release, ready)
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (automatic && runAttemptCount < 2) Result.retry() else Result.failure()
        } catch (error: Exception) {
            Result.failure()
        }
    }

    private fun notifyUpdate(version: String, ready: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!notifications.areNotificationsEnabled()) return false
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.OPEN_UPDATES, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 1002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (ready) "arXiV $version is ready" else "arXiV $version is available")
            .setContentText(if (ready) "Tap to review and install the downloaded update." else "Tap to review and download the update.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return try {
            notifications.notify(1002, notification)
            true
        } catch (_: SecurityException) {
            false // Keep the reminder pending if access was revoked.
        }
    }

    companion object {
        const val AUTOMATIC = "automatic"
        const val DOWNLOAD_ONLY = "download_only"
        private const val CHANNEL = "app_updates"
    }
}
