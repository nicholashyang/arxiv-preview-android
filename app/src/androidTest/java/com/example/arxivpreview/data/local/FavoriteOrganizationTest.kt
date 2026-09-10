package com.example.arxivpreview.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.data.FavoriteRepository
import com.example.arxivpreview.model.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteOrganizationTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: ArxivDatabase
    private val paper = Paper("2501.12948", "2501.12948v1", "Title", "Abstract", listOf("Author"), 1, 1, "cs.AI", listOf("cs.AI"), "https://arxiv.org/abs/2501.12948v1", "https://arxiv.org/pdf/2501.12948v1", null, null)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, ArxivDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun organizationRemovalUndoAndDeletionPreserveData() = runBlocking {
        val repo = FavoriteRepository(db)
        repo.saveGroup(value = " Project "); repo.saveTag(value = "Read"); repo.saveTag(value = "Methods")
        val group = repo.groups.first().single()
        val tags = repo.tags.first().map { it.id }.toSet()
        repo.organize(paper, group.id, tags)
        db.dao().upsertDownload(DownloadEntity(paper.id, paper.versionedId, "/kept.pdf", 10, 123))
        val original = db.dao().favorite(paper.id)!!
        val undo = repo.toggle(paper)!!
        assertNull(db.dao().favorite(paper.id)); assertTrue(db.dao().favoriteTags(paper.id).isEmpty())
        assertEquals("/kept.pdf", db.dao().getDownload(paper.id)!!.filePath)
        repo.restore(undo)
        assertEquals(original, db.dao().favorite(paper.id))
        assertEquals(tags, db.dao().favoriteTags(paper.id).map { it.tagId }.toSet())
        repo.deleteGroup(group.id)
        assertNull(db.dao().favorite(paper.id)!!.groupId)
        repo.deleteTag(tags.first())
        assertEquals(1, db.dao().favoriteTags(paper.id).size)
        assertNotNull(db.dao().favorite(paper.id))
    }
    @Test fun namesAreTrimmedAndUniqueAndProgressIsPerVersion() = runBlocking {
        val repo = FavoriteRepository(db)
        repo.saveGroup(value = " Study ")
        assertEquals("Study", repo.groups.first().single().name)
        assertTrue(runCatching { repo.saveGroup(value = "study") }.isFailure)
        assertTrue(runCatching { repo.saveTag(value = "   ") }.isFailure)
        repo.saveTag(value = "Study") // separate namespaces
        db.dao().saveProgress(HtmlProgressEntity("2501.12948v1", "S2", .2, .4))
        assertEquals("S2", db.dao().progress("2501.12948v1")!!.anchor)
        assertNull(db.dao().progress("2501.12948v2"))
    }
    @Test fun migrationFromVersionTwoKeepsOldFavoritesAndDownloads() {
        val name = "organization-migration"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.example.arxivpreview.data.local.ArxivDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO papers VALUES ('2501.12948','2501.12948v1','Title','Abstract','Author',1,1,'cs.AI','cs.AI','','',NULL,NULL)")
            sqlite.execSQL("INSERT INTO favorites VALUES ('2501.12948',123)")
            sqlite.execSQL("INSERT INTO downloads VALUES ('2501.12948','2501.12948v1','/kept.pdf',42,124)")
            sqlite.version = 2
        }
        val migrated = Room.databaseBuilder(context, ArxivDatabase::class.java, name).addMigrations(MIGRATION_2_3).build()
        try { runBlocking {
            assertEquals(123L, migrated.dao().favorite("2501.12948")!!.createdAt)
            assertNull(migrated.dao().favorite("2501.12948")!!.groupId)
            assertEquals("/kept.pdf", migrated.dao().getDownload("2501.12948")!!.filePath)
            assertTrue(migrated.dao().observeTags().first().isEmpty())
        } } finally { migrated.close() }
        context.deleteDatabase(name)
    }
}
