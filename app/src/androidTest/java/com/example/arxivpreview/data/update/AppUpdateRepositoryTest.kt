package com.example.arxivpreview.data.update

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "update-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "test.preferences_pb") }
    private val bytes = "This is not an APK".toByteArray()
    private var code = 200
    private var version = "99.0.0"
    private var assetId = 42L
    private val requests = mutableListOf<String>()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request().url.host
        val body = if (chain.request().url.host == "api.github.com") releaseJson().toByteArray() else bytes
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
            .message("Test response").body(body.toResponseBody()).build()
    }.build()
    private fun repository() = AppUpdateRepository(context, client, store, File(directory, "apks"))

    @After fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test fun manualCheckPersistsReleaseWithoutDownloading() = runBlocking {
        val repository = repository()
        assertTrue(repository.checkForUpdate() is UpdateResult.Available)
        val state = repository.state.first()
        assertEquals("99.0.0", state.release?.version)
        assertTrue(state.lastCheckedAt > 0)
        assertFalse(state.readyToInstall)
        assertEquals(UpdatePhase.IDLE, state.phase)
        assertEquals(state.release, repository().state.first().release)
    }

    @Test fun failedCheckRetainsPreviousReleaseAndTimestamp() = runBlocking {
        val repository = repository()
        repository.checkForUpdate()
        val previous = repository.state.first()
        code = 503
        assertThrows(IOException::class.java) {
            runBlocking { repository.checkForUpdate() }
        }
        val state = repository.state.first()
        assertEquals(previous.release, state.release)
        assertEquals(previous.lastCheckedAt, state.lastCheckedAt)
        assertNotNull(state.error)
        assertEquals(UpdatePhase.IDLE, state.phase)
    }

    @Test fun missingReleaseClearsPreviouslyAvailableUpdate() = runBlocking {
        val repository = repository()
        repository.checkForUpdate()
        code = 404
        repository.checkForUpdate()
        assertNull(repository.state.first().release)
        assertNull(repository.state.first().error)
    }

    @Test fun rateLimitIsReportedAndRetryCanRecover() = runBlocking {
        val repository = repository()
        code = 429
        assertThrows(IOException::class.java) {
            runBlocking { repository.checkForUpdate() }
        }
        assertTrue(repository.state.first().error!!.contains("limited"))
        code = 200
        repository.checkForUpdate()
        assertNull(repository.state.first().error)
        assertNotNull(repository.state.first().release)
    }

    @Test fun matchingChecksumCannotMakeANonApkInstallable() = runBlocking {
        val repository = repository()
        repository.checkForUpdate()
        assertThrows(IOException::class.java) {
            runBlocking { repository.downloadUpdate() }
        }
        val state = repository.state.first()
        assertFalse(state.readyToInstall)
        assertNotNull(state.error)
        assertFalse(File(directory, "apks/42.apk").exists())
        assertThrows(IOException::class.java) { runBlocking { repository.installIntent() } }
        Unit
    }

    @Test fun checkingNeverRequestsAnApk() = runBlocking {
        val repository = repository()
        repeat(2) { assertTrue(repository.checkForUpdate() is UpdateResult.Available) }
        assertEquals(listOf("api.github.com", "api.github.com"), requests)
        assertFalse(repository.state.first().readyToInstall)
        assertFalse(File(directory, "apks/42.apk").exists())
    }

    @Test fun reminderDecisionsPersistIndependentlyAcrossRestarts() = runBlocking {
        val repository = repository()
        val release = (repository.checkForUpdate() as UpdateResult.Available).release
        assertTrue(repository.shouldNotify(release, ready = false))
        repository.markNotified(release, ready = false)
        assertFalse(repository().shouldNotify(release, ready = false))
        assertTrue(repository().shouldNotify(release, ready = true))
        assertFalse(repository.state.first().promptDismissed)
        repository.dismissPrompt(release)
        assertTrue(repository().state.first().promptDismissed)
        repository.checkForUpdate()
        assertTrue(repository.state.first().promptDismissed)
        repository.markNotified(release, ready = true)
        assertFalse(repository().shouldNotify(release, ready = true))
    }

    @Test fun newVersionOrReplacedAssetCanRemindAgain() = runBlocking {
        val repository = repository()
        suspend fun dismissCurrent() {
            val release = (repository.checkForUpdate() as UpdateResult.Available).release
            repository.dismissPrompt(release)
            repository.markNotified(release, ready = false)
        }
        dismissCurrent()
        version = "99.1.0"
        val newer = (repository.checkForUpdate() as UpdateResult.Available).release
        assertFalse(repository.state.first().promptDismissed)
        assertTrue(repository.shouldNotify(newer, ready = false))
        dismissCurrent()
        assetId = 43
        val replaced = (repository.checkForUpdate() as UpdateResult.Available).release
        assertFalse(repository.state.first().promptDismissed)
        assertTrue(repository.shouldNotify(replaced, ready = false))
    }

    @Test fun blockedNotificationIsStillPendingAfterRestart() = runBlocking {
        val repository = repository()
        val release = (repository.checkForUpdate() as UpdateResult.Available).release
        // Delivery is blocked: do not acknowledge the event.
        assertTrue(repository().shouldNotify(release, ready = false))
        repository.dismissPrompt(release)
        assertTrue(repository().shouldNotify(release, ready = false))
    }

    private fun releaseJson(): String {
        val asset = JSONObject().put("id", assetId).put("name", "arxiv-preview-v$version.apk")
            .put("state", "uploaded").put("size", bytes.size)
            .put("digest", "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            .put("browser_download_url", "https://github.com/${AppRelease.REPOSITORY}/releases/download/v$version/arxiv-preview-v$version.apk")
        return JSONObject().put("tag_name", "v$version").put("body", "Test release")
            .put("assets", JSONArray().put(asset)).toString()
    }
}
