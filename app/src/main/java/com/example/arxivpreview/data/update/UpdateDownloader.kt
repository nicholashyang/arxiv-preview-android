package com.example.arxivpreview.data.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateDownloader(private val client: OkHttpClient) {
    suspend fun download(release: AppRelease, destination: File, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            val partial = File(destination.parentFile, "${destination.name}.part")
            try {
                client.withUpdateResponse(Request.Builder().url(release.downloadUrl).build()) { response ->
                    if (!response.isSuccessful) throw IOException("APK download failed (HTTP ${response.code}).")
                    if (!response.request.url.isHttps) throw IOException("An insecure APK redirect was rejected.")
                    val length = response.body.contentLength()
                    if (length > 0 && length != release.bytes) throw IOException("The APK size does not match the release.")
                    var copied = 0L
                    var lastProgress = -1
                    response.body.byteStream().use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > release.bytes) throw IOException("The APK exceeds its expected size.")
                                output.write(buffer, 0, count)
                                val progress = (copied * 100 / release.bytes).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                verify(partial, release)
                if (!partial.renameTo(destination)) throw IOException("Could not save the downloaded update.")
            } finally {
                partial.delete()
            }
        }

    companion object {
        fun verify(file: File, release: AppRelease) {
            if (!file.isFile || file.length() != release.bytes) throw IOException("The APK download is incomplete. Download it again.")
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != release.sha256) throw IOException("The APK checksum does not match. Download it again.")
        }
    }
}
