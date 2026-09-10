package com.example.arxivpreview

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.data.AppPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.arxivpreview.ui.ArxivApp
import com.example.arxivpreview.ui.theme.ArxivPreviewTheme

class MainActivity : FragmentActivity() {
    private var openUpdates by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeUpdateIntent(intent)
        enableEdgeToEdge()
        setContent {
            val container = (application as ArxivApplication).container
            val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
            ArxivPreviewTheme(preferences?.themeMode ?: com.example.arxivpreview.data.ThemeMode.SYSTEM) {
                if (preferences != null) ArxivApp(
                    application = application,
                    container = (application as ArxivApplication).container,
                    openUpdates = openUpdates,
                    onUpdatesOpened = { openUpdates = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeUpdateIntent(intent)
    }

    private fun consumeUpdateIntent(intent: Intent) {
        if (intent.getBooleanExtra(OPEN_UPDATES, false)) {
            openUpdates = true
            intent.removeExtra(OPEN_UPDATES)
        }
    }

    companion object {
        const val OPEN_UPDATES = "open_app_updates"
    }
}
