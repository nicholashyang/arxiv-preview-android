package com.example.arxivpreview.data.update

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.arxivpreview.data.PreferencesRepository
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
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val body = if (chain.request().url.host == "api.github.com") releaseJson().toByteArray() else bytes
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
            .message("Test response").body(body.toResponseBody()).build()
    }.build()
    private fun repository() = AppUpdateRepository(context, client, PreferencesRepository(context), store, File(directory, "apks"))

    @After fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test fun manualCheckPersistsReleaseWithoutDownloading() = runBlocking {
        val repository = repository()
        assertNull(repository.runUpdate(downloadOnly = false, automatic = false))
        val state = repository.state.first()
        assertEquals("99.0.0", state.release?.version)
        assertTrue(state.lastCheckedAt > 0)
        assertFalse(state.readyToInstall)
        assertEquals(UpdatePhase.IDLE, state.phase)
        assertEquals(state.release, repository().state.first().release)
    }

    @Test fun failedCheckRetainsPreviousReleaseAndTimestamp() = runBlocking {
        val repository = repository()
        repository.runUpdate(downloadOnly = false, automatic = false)
        val previous = repository.state.first()
        code = 503
        assertThrows(IOException::class.java) {
            runBlocking { repository.runUpdate(downloadOnly = false, automatic = false) }
        }
        val state = repository.state.first()
        assertEquals(previous.release, state.release)
        assertEquals(previous.lastCheckedAt, state.lastCheckedAt)
        assertNotNull(state.error)
        assertEquals(UpdatePhase.IDLE, state.phase)
    }

    @Test fun missingReleaseClearsPreviouslyAvailableUpdate() = runBlocking {
        val repository = repository()
        repository.runUpdate(downloadOnly = false, automatic = false)
        code = 404
        repository.runUpdate(downloadOnly = false, automatic = false)
        assertNull(repository.state.first().release)
        assertNull(repository.state.first().error)
    }

    @Test fun rateLimitIsReportedAndRetryCanRecover() = runBlocking {
        val repository = repository()
        code = 429
        assertThrows(IOException::class.java) {
            runBlocking { repository.runUpdate(downloadOnly = false, automatic = false) }
        }
        assertTrue(repository.state.first().error!!.contains("limited"))
        code = 200
        repository.runUpdate(downloadOnly = false, automatic = false)
        assertNull(repository.state.first().error)
        assertNotNull(repository.state.first().release)
    }

    @Test fun matchingChecksumCannotMakeANonApkInstallable() = runBlocking {
        val repository = repository()
        repository.runUpdate(downloadOnly = false, automatic = false)
        assertThrows(IOException::class.java) {
            runBlocking { repository.runUpdate(downloadOnly = true, automatic = false) }
        }
        val state = repository.state.first()
        assertFalse(state.readyToInstall)
        assertNotNull(state.error)
        assertFalse(File(directory, "apks/42.apk").exists())
        assertThrows(IOException::class.java) { runBlocking { repository.installIntent() } }
        Unit
    }

    private fun releaseJson(): String {
        val asset = JSONObject().put("id", 42).put("name", "arxiv-preview-v99.0.0.apk")
            .put("state", "uploaded").put("size", bytes.size)
            .put("digest", "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            .put("browser_download_url", "https://github.com/${AppRelease.REPOSITORY}/releases/download/v99.0.0/arxiv-preview-v99.0.0.apk")
        return JSONObject().put("tag_name", "v99.0.0").put("body", "Test release")
            .put("assets", JSONArray().put(asset)).toString()
    }
}
