package com.example.arxivpreview.data.remote

import com.example.arxivpreview.model.PaperPage
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ArxivRemoteDataSource(
    private val service: ArxivService,
    private val parser: ArxivParser,
    private val minimumIntervalMillis: Long = 3_000,
) {
    private val gate = Mutex()
    private var lastRequestAt = 0L

    suspend fun search(
        query: String,
        start: Int,
        maxResults: Int = 20,
        sortBy: String = "submittedDate",
        sortOrder: String = "descending",
    ): PaperPage = gate.withLock {
        val wait = minimumIntervalMillis - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                val body = service.search(query, start, maxResults, sortBy, sortOrder)
                lastRequestAt = System.currentTimeMillis()
                body.use { return@withLock parser.parse(it.byteStream()) }
            } catch (error: Throwable) {
                lastError = error
                if (attempt < 2) delay(1_000L shl attempt)
            }
        }
        throw IOException("arXiv request failed", lastError)
    }
}
