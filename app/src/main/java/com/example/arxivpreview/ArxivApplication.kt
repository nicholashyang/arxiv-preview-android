package com.example.arxivpreview

import android.app.Application
import com.example.arxivpreview.worker.AppUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ArxivApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // AndroidX PDF runs a separate service process without the main WorkManager initializer.
        if (getProcessName() != packageName) return
        applicationScope.launch {
            container.preferencesRepository.preferences.map { it.automaticAppUpdates }
                .distinctUntilChanged().collect { enabled ->
                    AppUpdateScheduler.setAutomatic(this@ArxivApplication, enabled)
                }
        }
    }
}
