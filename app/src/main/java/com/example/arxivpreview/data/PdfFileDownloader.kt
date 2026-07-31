package com.example.arxivpreview.data

import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

class PdfFileDownloader(private val client: OkHttpClient) {
    fun download(url: String, destination: File, onProgress: (Int) -> Unit = {}): Long {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        if (partial.exists()) partial.delete()
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) throw IOException("PDF download failed: HTTP ${it.code}")
                val body = it.body
                val total = body.contentLength()
                var copied = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress(((copied * 100) / total).toInt())
                        }
                    }
                }
                validatePdf(partial)
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Could not replace existing PDF")
                }
                if (!partial.renameTo(destination)) throw IOException("Could not finalize PDF")
                onProgress(100)
                return copied
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private fun validatePdf(file: File) {
        if (file.length() < 5) throw IOException("Downloaded file is empty")
        val header = file.inputStream().use { input ->
            ByteArray(5).also { input.read(it) }.decodeToString()
        }
        if (header != "%PDF-") throw IOException("Downloaded file is not a PDF")
    }
}
