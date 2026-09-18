package com.example.arxivpreview.data.discovery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.arxivpreview.data.local.ArxivDatabase
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class SemanticScholarClientTest {
    private lateinit var db: ArxivDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArxivDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun cacheCoalescesConcurrentRequestsAndStaleFallbackKeepsTimestamp() = runBlocking {
        var calls = 0; var offline = false; var clock = 1000L
        val client = SemanticScholarClient(db.discoveryDao(), SemanticTransport { _,_,_,key ->
            assertEquals("personal-fixture-key", key); calls++
            if (offline) throw SemanticHttpException(403)
            """{"authorId":"1","name":"Author"}"""
        }, { "personal-fixture-key" }, 0) { clock }
        coroutineScope { repeat(3) { launch { client.get("graph/v1/author/1") } } }
        assertEquals(1, calls)
        offline = true; clock += SemanticScholarClient.DAY + 1
        val stale = client.get("graph/v1/author/1")
        assertTrue(stale.stale); assertEquals(1000L, stale.fetchedAt)
        assertNotNull(client.warning.value)
        assertTrue(runCatching { client.get("graph/v1/author/1", force = true, allowStale = false) }.isFailure)
    }
    @Test fun restrictedAndUnindexedRequestsAreNotRetried() = runBlocking {
        for (status in listOf(401,403,404,429)) {
            var calls = 0
            val client = SemanticScholarClient(db.discoveryDao(), SemanticTransport { _,_,_,key -> assertNull(key); calls++; throw SemanticHttpException(status, 60) }, { null }, 0)
            val result = runCatching { client.get("status/$status") }
            assertEquals(status, (result.exceptionOrNull() as SemanticHttpException).status)
            assertEquals(1, calls)
        }
    }
    @Test fun cancellationStopsWithoutRetryOrCacheWrite() = runBlocking {
        val started = CompletableDeferred<Unit>(); var calls = 0
        val client = SemanticScholarClient(db.discoveryDao(), SemanticTransport { _,_,_,_ -> calls++; started.complete(Unit); awaitCancellation() }, { null }, 0)
        val job = launch { client.get("cancellation") }
        started.await(); job.cancelAndJoin()
        assertEquals(1, calls)
    }
    @Test fun temporaryFailureRetriesAndParsesMissingDatesAndLegacyIds() = runBlocking {
        var calls = 0
        val client = SemanticScholarClient(db.discoveryDao(), SemanticTransport { _,_,_,_ ->
            if (calls++ == 0) throw SemanticHttpException(429, 0)
            """{"paperId":"paper","externalIds":{"ArXiv":"hep-th/9901001v2"},"title":"Title","publicationDate":null,"year":null}"""
        }, { null }, 0)
        val work = AuthorRepository.work(client.get("retry").payload)!!
        assertEquals(2, calls); assertEquals("hep-th/9901001", work.arxivId)
        assertNull(work.year); assertNull(work.date)
        assertNull(AuthorRepository.summary(JSONObject("""{"name":"Anonymous","authorId":null}""")))
    }
    @Test fun keyIsEncryptedAndSurvivesStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SemanticKeyStore(context)
        val previous = store.read()
        try {
            store.save("test-personal-secret")
            assertEquals("test-personal-secret", SemanticKeyStore(context).read())
            assertFalse(java.io.File(context.noBackupFilesDir, "semantic-key").readText().contains("test-personal-secret"))
            store.delete(); assertNull(store.read())
        } finally { if (previous != null) store.save(previous) else store.delete() }
    }
}
