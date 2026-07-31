package com.example.arxivpreview.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.arxivpreview.model.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArxivDatabaseTest {
    private lateinit var database: ArxivDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ArxivDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun favoriteAndDownloadRemainIndependent() = runBlocking {
        val paper = samplePaper()
        val dao = database.dao()
        dao.upsertPaper(paper.toEntity())
        dao.addFavorite(FavoriteEntity(paper.id, 1))
        dao.upsertDownload(DownloadEntity(paper.id, paper.versionedId, "/tmp/paper.pdf", 10, 2))

        dao.removeFavorite(paper.id)

        assertFalse(dao.observeIsFavorite(paper.id).first())
        assertEquals("/tmp/paper.pdf", dao.getDownload(paper.id)?.filePath)
        assertTrue(dao.observeFavorites().first().isEmpty())
    }

    private fun samplePaper() = Paper(
        id = "2607.12345",
        versionedId = "2607.12345v1",
        title = "Test",
        summary = "Abstract",
        authors = listOf("Author"),
        publishedAt = 1,
        updatedAt = 1,
        primaryCategory = "cs.AI",
        categories = listOf("cs.AI"),
        abstractUrl = "https://arxiv.org/abs/2607.12345v1",
        pdfUrl = "https://arxiv.org/pdf/2607.12345v1",
        doi = null,
        journalReference = null,
    )
}
