package com.example.arxivpreview.data.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "APK bytes for integrity checks".toByteArray()
    private val release = AppRelease(
        "1.1.0", "", 42, "https://github.com/example/update.apk", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
    )

    @Test fun validDownloadIsFinalizedAndReportsProgress() = runBlocking {
        val file = File(folder.root, "update.apk")
        val progress = mutableListOf<Int>()
        downloader().download(release, file) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(100, progress.last())
        assertFalse(File(folder.root, "update.apk.part").exists())
    }

    @Test fun checksumMismatchNeverReplacesExistingFile() {
        val file = folder.newFile("update.apk").apply { writeText("previous download") }
        assertThrows(IOException::class.java) {
            runBlocking { downloader().download(release.copy(sha256 = "0".repeat(64)), file) {} }
        }
        assertEquals("previous download", file.readText())
        assertFalse(File(folder.root, "update.apk.part").exists())
    }

    @Test fun truncatedAndOversizedDownloadsAreRejected() {
        listOf(bytes.dropLast(1).toByteArray(), bytes + 1.toByte()).forEach { body ->
            val file = File(folder.root, "update.apk")
            assertThrows(IOException::class.java) {
                runBlocking { downloader(body).download(release, file) {} }
            }
            assertFalse(file.exists())
        }
    }

    @Test fun httpFailuresLeaveNoApk() {
        val file = File(folder.root, "update.apk")
        assertThrows(IOException::class.java) {
            runBlocking { downloader(code = 503).download(release, file) {} }
        }
        assertFalse(file.exists())
    }

    @Test fun cancellationRemovesPartialFileAndPropagates() {
        val file = File(folder.root, "update.apk")
        assertThrows(CancellationException::class.java) {
            runBlocking { downloader().download(release, file) { throw CancellationException("Cancelled") } }
        }
        assertFalse(file.exists())
        assertFalse(File(folder.root, "update.apk.part").exists())
    }

    @Test fun persistedApkIsRecheckedBeforeUse() {
        val file = folder.newFile("update.apk").apply { writeBytes(bytes) }
        UpdateDownloader.verify(file, release)
        file.writeBytes(ByteArray(bytes.size))
        assertThrows(IOException::class.java) { UpdateDownloader.verify(file, release) }
    }

    private fun downloader(body: ByteArray = bytes, code: Int = 200) = UpdateDownloader(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test response").body(body.toResponseBody()).build()
        }.build(),
    )
}
