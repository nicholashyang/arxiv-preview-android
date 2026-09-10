package com.example.arxivpreview

import android.app.Application
import com.example.arxivpreview.worker.AppUpdateScheduler

class ArxivApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // AndroidX PDF runs a separate service process without the main WorkManager initializer.
        if (getProcessName() != packageName) return
        AppUpdateScheduler.scheduleAutomaticChecks(this)
    }
}
