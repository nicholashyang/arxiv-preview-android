package com.example.arxivpreview.data.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.arxivpreview.BuildConfig
import com.example.arxivpreview.data.PreferencesRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.updateDataStore by preferencesDataStore("app_updates")

enum class UpdatePhase { IDLE, CHECKING, DOWNLOADING, VERIFYING }

data class AppUpdateState(
    val release: AppRelease? = null,
    val lastCheckedAt: Long = 0,
    val readyToInstall: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val progress: Int = 0,
    val error: String? = null,
)

class AppUpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val preferences: PreferencesRepository,
    private val store: DataStore<Preferences> = context.updateDataStore,
    private val directory: File = File(context.filesDir, "updates"),
) {
    private val mutex = Mutex()
    private val activity = MutableStateFlow(UpdatePhase.IDLE to 0)
    private val downloader = UpdateDownloader(client)
    private fun apkFile(release: AppRelease) = File(directory, "${release.assetId}.apk")

    private val savedState = store.data.map { values ->
        val release = values[RELEASE_JSON]?.let {
            runCatching { AppRelease.parseGithub(it, BuildConfig.VERSION_NAME) }.getOrNull()
        }
        AppUpdateState(
            release = release,
            lastCheckedAt = values[LAST_CHECKED] ?: 0,
            readyToInstall = release != null && values[DOWNLOADED_ASSET] == release.assetId &&
                apkFile(release).isFile && apkFile(release).length() == release.bytes,
            error = values[ERROR],
        )
    }
    val state = combine(savedState, activity) { saved, running ->
        saved.copy(phase = running.first, progress = running.second)
    }

    /** Returns a release only when this run downloaded a new APK, for one notification. */
    suspend fun runUpdate(downloadOnly: Boolean, automatic: Boolean): AppRelease? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (automatic && !preferences.preferences.first().automaticAppUpdates) return@withContext null
            store.edit { it.remove(ERROR) }
            try {
                val release = if (downloadOnly) {
                    savedState.first().release ?: throw IOException("Check for an update before downloading.")
                } else {
                    activity.value = UpdatePhase.CHECKING to 0
                    checkLatest()
                }
                if (release == null || (!downloadOnly && !automatic)) return@withContext null
                if (automatic && !preferences.preferences.first().automaticAppUpdates) return@withContext null
                if (savedState.first().readyToInstall) return@withContext null
                activity.value = UpdatePhase.DOWNLOADING to 0
                downloader.download(release, apkFile(release)) { activity.value = UpdatePhase.DOWNLOADING to it }
                activity.value = UpdatePhase.VERIFYING to 100
                try {
                    validateApk(apkFile(release), release)
                } catch (error: Exception) {
                    apkFile(release).delete()
                    throw error
                }
                currentCoroutineContext().ensureActive()
                store.edit { it[DOWNLOADED_ASSET] = release.assetId }
                directory.listFiles()?.filter { it != apkFile(release) }?.forEach { it.delete() }
                release
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                reportError(error.message ?: "Could not update the app. Try again.")
                throw error
            } finally {
                activity.value = UpdatePhase.IDLE to 0
            }
        }
    }

    private suspend fun checkLatest(): AppRelease? {
        val request = Request.Builder().url(AppRelease.API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val json = client.withUpdateResponse(request) { response ->
            when {
                response.code == 404 -> null // No published release yet.
                response.code == 403 || response.code == 429 ->
                    throw IOException("GitHub temporarily limited update checks. Please try again later.")
                !response.isSuccessful -> throw IOException("Update check failed (HTTP ${response.code}).")
                else -> {
                    val source = response.body.source()
                    source.request(1_048_577)
                    if (source.buffer.size > 1_048_576) throw IOException("The update response is too large.")
                    response.body.string()
                }
            }
        }
        currentCoroutineContext().ensureActive()
        val release = json?.let { AppRelease.parseGithub(it, BuildConfig.VERSION_NAME) }
        val previous = savedState.first().release
        store.edit {
            if (release == null) it.remove(RELEASE_JSON) else it[RELEASE_JSON] = requireNotNull(json)
            if (release?.assetId != previous?.assetId || release?.sha256 != previous?.sha256) {
                it.remove(DOWNLOADED_ASSET)
            }
            it[LAST_CHECKED] = System.currentTimeMillis()
        }
        if (release == null) directory.listFiles()?.forEach { it.delete() }
        return release
    }

    suspend fun reportError(message: String) {
        store.edit { it[ERROR] = message }
    }

    suspend fun installIntent(): Intent = mutex.withLock {
        withContext(Dispatchers.IO) {
            val saved = savedState.first()
            val release = saved.release ?: throw IOException("No update is available. Check again.")
            if (!saved.readyToInstall) throw IOException("Download the update before installing.")
            activity.value = UpdatePhase.VERIFYING to 100
            try {
                val file = apkFile(release)
                try {
                    UpdateDownloader.verify(file, release)
                    validateApk(file, release)
                } catch (error: Exception) {
                    file.delete()
                    store.edit { it.remove(DOWNLOADED_ASSET) }
                    throw error
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                store.edit { it.remove(ERROR) }
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    clipData = ClipData.newRawUri("App update", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } finally {
                activity.value = UpdatePhase.IDLE to 0
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, release: AppRelease) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("The download is not a valid Android APK.")
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.packageName != context.packageName) throw IOException("The APK belongs to a different app.")
        if (archive.longVersionCode <= installed.longVersionCode) throw IOException("The APK is not newer than the installed app.")
        if (ReleaseVersion.parse(archive.versionName.orEmpty()) != ReleaseVersion.parse(release.version)) {
            throw IOException("The APK version does not match its release.")
        }
        if ((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) {
            throw IOException("This update requires a newer Android version.")
        }
        val expected = installed.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        val actual = archive.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        if (expected.isEmpty() || expected != actual) {
            throw IOException("The APK signing certificate does not match this installation.")
        }
    }

    private companion object {
        val RELEASE_JSON = stringPreferencesKey("release_json")
        val LAST_CHECKED = longPreferencesKey("last_checked")
        val DOWNLOADED_ASSET = longPreferencesKey("downloaded_asset")
        val ERROR = stringPreferencesKey("error")
    }
}
