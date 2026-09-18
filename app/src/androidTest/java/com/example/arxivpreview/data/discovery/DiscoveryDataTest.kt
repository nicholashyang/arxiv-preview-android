package com.example.arxivpreview.data.discovery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.data.*
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.data.remote.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class DiscoveryDataTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: ArxivDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, ArxivDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun schemaThreeMigrationPreservesOrganizationReadingAndNewStatesAcrossReopen() = runBlocking {
        val name = "discovery-migration-test"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { sql ->
            val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.example.arxivpreview.data.local.ArxivDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sql.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.execSQL("INSERT INTO papers VALUES ('2609.00001','2609.00001v1','Title','Abstract','Author',1,1,'cs.AI','cs.AI','','',NULL,NULL)")
            sql.execSQL("INSERT INTO favorite_groups VALUES (1,'Work','work')")
            sql.execSQL("INSERT INTO tags VALUES (1,'Read','read')")
            sql.execSQL("INSERT INTO favorites VALUES ('2609.00001',123,1)")
            sql.execSQL("INSERT INTO favorite_tags VALUES ('2609.00001',1)")
            sql.execSQL("INSERT INTO downloads VALUES ('2609.00001','2609.00001v1','/kept.pdf',42,124)")
            sql.execSQL("INSERT INTO html_progress VALUES ('2609.00001v1','S2',0.2,0.4)")
            sql.version = 3
        }
        fun open() = Room.databaseBuilder(context, ArxivDatabase::class.java, name).addMigrations(MIGRATION_3_4).build()
        val migrated = open()
        assertEquals(1L, migrated.dao().favorite("2609.00001")!!.groupId)
        assertEquals(1, migrated.dao().favoriteTags("2609.00001").size)
        assertEquals("/kept.pdf", migrated.dao().getDownload("2609.00001")!!.filePath)
        assertEquals("S2", migrated.dao().progress("2609.00001v1")!!.anchor)
        migrated.discoveryDao().insertInbox(InboxEntity("2609.00001", listOf("Topic: QCD"), 100))
        migrated.discoveryDao().read("2609.00001", true); migrated.discoveryDao().archive("2609.00001", true)
        migrated.discoveryDao().dismiss(FeedbackEntity("2609.00001", 5))
        migrated.discoveryDao().saveAuthor(AuthorEntity("123", "Author", emptyList(), null, null, null, 1))
        migrated.discoveryDao().follow(FollowedAuthorEntity("123", 2))
        migrated.close()
        val reopened = open()
        assertTrue(reopened.discoveryDao().inbox("2609.00001")!!.archived)
        assertTrue(reopened.discoveryDao().inbox("2609.00001")!!.isRead)
        assertEquals(1, reopened.discoveryDao().observeFeedback().first().size)
        assertEquals("123", reopened.discoveryDao().follows().single().authorId)
        reopened.close(); context.deleteDatabase(name); Unit
    }

    @Test fun paginatedBaselineIsSilentAndSubsequentUpdatesMergeWithoutResettingUserState() = runBlocking {
        val preferences = PreferencesRepository(context)
        val before = preferences.preferences.first()
        try {
            preferences.setCategories(setOf("hep-ph")); preferences.setOnlineRecommendations(false)
            var clock = Instant.parse("2026-09-11T08:00:00Z").toEpochMilli()
            val ids = mutableListOf("2609.00001", "2609.00002")
            val starts = mutableListOf<Int>()
            var failAt: Int? = null
            val remote = object : ArxivService {
                override suspend fun search(query: String?, idList: String?, start: Int, maxResults: Int, sortBy: String, sortOrder: String): ResponseBody {
                    starts += start
                    if (start == failAt) throw java.io.IOException("Fixture connection lost")
                    val id = ids[start]
                    return """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/"><opensearch:totalResults>${ids.size}</opensearch:totalResults><entry><id>https://arxiv.org/abs/${id}v1</id><title>Parton distributions</title><summary>QCD</summary><published>2026-09-11T07:00:00Z</published><updated>2026-09-11T07:00:00Z</updated><author><name>Author</name></author><category term="hep-ph"/></entry></feed>""".toResponseBody()
                }
            }
            val paperRepo = PaperRepository(db, db.dao(), ArxivRemoteDataSource(remote, ArxivParser(), 0))
            val api = SemanticScholarClient(db.discoveryDao(), SemanticTransport { _,_,_,_ -> error("No online access expected") }, { null }, 0)
            val authorRepo = AuthorRepository(db.discoveryDao(), api, paperRepo)
            val recommendation = RecommendationRepository(db, preferences, paperRepo, api)
            val sync = DiscoverySyncCoordinator(db, preferences, paperRepo, authorRepo, recommendation) { clock }
            assertEquals(0, sync.refresh().newCount)
            assertEquals(listOf(0,1), starts)
            assertEquals(2, db.discoveryDao().observeInbox().first().size)
            db.discoveryDao().read(ids[0], true); db.discoveryDao().archive(ids[0], true)
            clock += 1000; ids += "2609.00003"
            failAt = 1
            assertTrue(sync.refresh().pending)
            assertEquals(1, db.discoveryDao().sync("category:hep-ph")!!.nextStart)
            failAt = null
            assertEquals(1, sync.refresh().newCount)
            assertEquals(0, sync.refresh().newCount)
            assertTrue(db.discoveryDao().inbox(ids[0])!!.isRead)
            assertTrue(db.discoveryDao().inbox(ids[0])!!.archived)
            val paper = paperRepo.getPaper(ids[0])!!
            assertFalse(sync.ingest(paper, "keyword:qcd", "Topic: qcd", false))
            assertEquals(2, db.discoveryDao().inbox(ids[0])!!.reasons.size)
            assertTrue(db.discoveryDao().sync("category:hep-ph")!!.initialized)
            assertEquals(0L, db.discoveryDao().sync("category:hep-ph")!!.windowEnd)
        } finally {
            if (before.categories.isNotEmpty()) preferences.setCategories(before.categories)
            preferences.setOnlineRecommendations(before.onlineRecommendations)
        }
    }
}
