package com.example.arxivpreview.data.remote

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArxivRequestPolicyTest {
    @Test fun cancellationIsNotRetriedAndExactIdUsesSeparateParameter() = runBlocking {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val service = object : ArxivService {
            override suspend fun search(query: String?, idList: String?, start: Int, maxResults: Int, sortBy: String, sortOrder: String): ResponseBody {
                calls++
                assertNull(query); assertEquals("hep-th/9901001v2", idList)
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val remote = ArxivRemoteDataSource(service, ArxivParser(), 0)
        val job = launch { remote.search(null, 0, idList = "hep-th/9901001v2") }
        started.await(); job.cancelAndJoin()
        assertEquals(1, calls)
    }
    @Test fun requestsAreSerializedAndSpaced() = runBlocking {
        val times = mutableListOf<Long>()
        var active = 0
        val service = object : ArxivService {
            override suspend fun search(query: String?, idList: String?, start: Int, maxResults: Int, sortBy: String, sortOrder: String): ResponseBody {
                active++; assertEquals(1, active); times += SystemClock.elapsedRealtime()
                delay(20); active--
                return """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/"><opensearch:totalResults>0</opensearch:totalResults><opensearch:startIndex>0</opensearch:startIndex><opensearch:itemsPerPage>0</opensearch:itemsPerPage></feed>""".toResponseBody()
            }
        }
        val remote = ArxivRemoteDataSource(service, ArxivParser(), 100)
        coroutineScope { repeat(3) { launch { remote.search("all:test", 0) } } }
        assertEquals(3, times.size)
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= 100 })
    }
}
