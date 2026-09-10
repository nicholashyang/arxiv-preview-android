package com.example.arxivpreview.ui

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.data.PreferencesRepository
import com.example.arxivpreview.data.update.AppRelease
import com.example.arxivpreview.data.update.AppUpdateRepository
import com.example.arxivpreview.worker.AppUpdateScheduler
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class UpdateReminderTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun deniedSystemNotificationsStillAllowDismissalAndNewAssetReminder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runBlocking { PreferencesRepository(context).finishOnboarding(setOf("cs.AI")) }
        WorkManager.getInstance(context).cancelUniqueWork(AppUpdateScheduler.AUTOMATIC_WORK).result.get(10, TimeUnit.SECONDS)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.executeShellCommand("pm revoke ${context.packageName} android.permission.POST_NOTIFICATIONS").use {
                FileInputStream(it.fileDescriptor).readBytes()
            }
        }
        var asset = System.currentTimeMillis()
        var status = 200
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val apk = JSONObject().put("id", asset).put("name", "arxiv-preview-v99.0.0.apk")
                .put("state", "uploaded").put("size", 100)
                .put("digest", "sha256:" + "a".repeat(64))
                .put("browser_download_url", "https://github.com/${AppRelease.REPOSITORY}/releases/download/v99.0.0/arxiv-preview-v99.0.0.apk")
            val json = JSONObject().put("tag_name", "v99.0.0").put("assets", JSONArray().put(apk))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("Fixture").body(json.toString().toResponseBody()).build()
        }.build()
        // The default repository shares the app's DataStore; only this test's HTTP client is fake.
        val repository = AppUpdateRepository(context, client)
        try {
            runBlocking { repository.checkForUpdate() }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                compose.waitUntil(10000) { compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Later").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithText("Later").fetchSemanticsNodes().isEmpty() }
                scenario.recreate()
                compose.onNodeWithText("Later").assertDoesNotExist()
                asset += 1
                runBlocking { repository.checkForUpdate() }
                compose.waitUntil(10000) { compose.onAllNodesWithText("View update").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("View update").performClick()
                compose.waitUntil(10000) {
                    runCatching { compose.onNodeWithText("Check for updates").assertIsDisplayed(); true }.getOrDefault(false)
                }
                compose.onNodeWithText("Later").assertDoesNotExist()
                compose.onNodeWithText("Download", substring = false).assertIsDisplayed()
            }
        } finally {
            status = 404
            runBlocking { repository.checkForUpdate() }
            AppUpdateScheduler.scheduleAutomaticChecks(context)
        }
    }
}
