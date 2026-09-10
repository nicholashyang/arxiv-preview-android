package com.example.arxivpreview.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AppUpdateSchedulerTest {
    @Test fun alwaysSchedulesConnectedChecksAndReusesExistingPeriodicWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        AppUpdateScheduler.scheduleAutomaticChecks(context).result.get(10, TimeUnit.SECONDS)
        val before = manager.getWorkInfosForUniqueWork(AppUpdateScheduler.AUTOMATIC_WORK).get(10, TimeUnit.SECONDS)
            .single { !it.state.isFinished }
        assertEquals(NetworkType.CONNECTED, before.constraints.requiredNetworkType)
        assertFalse(before.constraints.requiresStorageNotLow())
        assertEquals(TimeUnit.HOURS.toMillis(24), before.periodicityInfo!!.repeatIntervalMillis)
        AppUpdateScheduler.scheduleAutomaticChecks(context).result.get(10, TimeUnit.SECONDS)
        val after = manager.getWorkInfosForUniqueWork(AppUpdateScheduler.AUTOMATIC_WORK).get(10, TimeUnit.SECONDS)
            .single { !it.state.isFinished }
        assertEquals(before.id, after.id)
    }
}
