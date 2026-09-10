package com.example.arxivpreview.data.local

import androidx.room.Room
import org.json.JSONObject
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyIdMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun repairsLegacyKeysWithoutLosingFavoritesOrOfflineFiles() {
        val name = "legacy-id-migration-test"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).apply {
            val schema = JSONObject(instrumentation.context.assets.open("com.example.arxivpreview.data.local.ArxivDatabase/1.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            version = 1
            execSQL("""INSERT INTO papers VALUES ('9901001', '9901001v2', 'Legacy paper', 'Abstract', 'Author', 1, 2,
                'hep-th', 'hep-th', 'https://arxiv.org/abs/hep-th/9901001v2', 'https://arxiv.org/pdf/hep-th/9901001v2', NULL, NULL)""")
            execSQL("INSERT INTO favorites VALUES ('9901001', 123)")
            execSQL("INSERT INTO downloads VALUES ('9901001', '9901001v2', '/preserved/offline.pdf', 50, 124)")
            execSQL("INSERT INTO feed_items VALUES ('9901001', 0, 125)")
            close()
        }
        val room = Room.databaseBuilder(context, ArxivDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        room.openHelper.writableDatabase.apply {
            query("SELECT id, versionedId FROM papers").use {
                assertTrue(it.moveToFirst()); assertEquals("hep-th/9901001", it.getString(0)); assertEquals("hep-th/9901001v2", it.getString(1))
            }
            query("SELECT paperId, filePath FROM downloads").use {
                assertTrue(it.moveToFirst()); assertEquals("hep-th/9901001", it.getString(0)); assertEquals("/preserved/offline.pdf", it.getString(1))
            }
            for (table in listOf("favorites", "feed_items")) query("SELECT paperId FROM $table").use {
                assertTrue(it.moveToFirst()); assertEquals("hep-th/9901001", it.getString(0))
            }
            query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        room.close()
        context.deleteDatabase(name)
    }
}
