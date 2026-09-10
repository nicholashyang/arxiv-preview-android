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
import com.example.arxivpreview.R
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class AppUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ArxivApplication).container
        val automatic = inputData.getBoolean(AUTOMATIC, false)
        return try {
            val downloaded = container.appUpdateRepository.runUpdate(
                downloadOnly = inputData.getBoolean(DOWNLOAD_ONLY, false),
                automatic = automatic,
            )
            if (downloaded != null &&
                (!automatic || container.preferencesRepository.preferences.first().automaticAppUpdates)
            ) notifyReady(downloaded.version)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (automatic && runAttemptCount < 2) Result.retry() else Result.failure()
        } catch (error: Exception) {
            Result.failure()
        }
    }

    private fun notifyReady(version: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT),
        )
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
            .setContentTitle("arXiV $version is ready")
            .setContentText("Tap to review and install the downloaded update.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(1002, notification)
        } catch (_: SecurityException) {
            // Notification access may have been revoked; the APK remains available in Settings.
        }
    }

    companion object {
        const val AUTOMATIC = "automatic"
        const val DOWNLOAD_ONLY = "download_only"
        private const val CHANNEL = "app_updates"
    }
}
