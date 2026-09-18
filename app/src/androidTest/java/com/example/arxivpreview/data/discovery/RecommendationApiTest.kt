package com.example.arxivpreview.data.discovery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.arxivpreview.data.*
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.data.remote.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class RecommendationApiTest {
    @Test fun usesResolvedSeedIdsAndHydratesOnlyArxivResultsThenInvalidatesOnUnsave() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesRepository(context)
        val before = prefs.preferences.first()
        val db = Room.inMemoryDatabaseBuilder(context, ArxivDatabase::class.java).build()
        try {
            prefs.setCategories(setOf("hep-ph")); prefs.setOnlineRecommendations(true)
            val paperRepo = PaperRepository(db, db.dao(), ArxivRemoteDataSource(object : ArxivService {
                override suspend fun search(query: String?, idList: String?, start: Int, maxResults: Int, sortBy: String, sortOrder: String): ResponseBody = error("All metadata is cached")
            }, ArxivParser(), 0))
            val now = System.currentTimeMillis()
            for (id in listOf("2609.00101", "2609.00102", "2609.00103")) {
                db.dao().upsertPaper(PaperEntity(id, "${id}v1", "Topics", "Abstract", listOf("Author"), now, now, "math.SG", listOf("math.SG"), "", "", null, null))
            }
            db.dao().addFavorite(FavoriteEntity("2609.00101", now))
            db.discoveryDao().dismiss(FeedbackEntity("2609.00102", now))
            var recommendationCalls = 0
            val client = SemanticScholarClient(db.discoveryDao(), SemanticTransport { path,_,body,_ ->
                if (path.startsWith("graph/v1/paper/ARXIV:")) {
                    val id = path.substringAfter("ARXIV:")
                    """{"paperId":"s2-$id"}"""
                } else {
                    recommendationCalls++
                    val request = JSONObject(body!!)
                    assertEquals("s2-2609.00101", request.getJSONArray("positivePaperIds").getString(0))
                    assertEquals("s2-2609.00102", request.getJSONArray("negativePaperIds").getString(0))
                    """{"recommendedPapers":[{"paperId":"s2-2609.00103","title":"Topics","externalIds":{"ArXiv":"2609.00103v2"}},{"paperId":"other","title":"External","externalIds":{"DOI":"example"}}]}"""
                }
            }, { null }, 0)
            val repo = RecommendationRepository(db, prefs, paperRepo, client)
            repo.enhance()
            val items = repo.recommendations.first()
            assertEquals(listOf("2609.00103"), items.map { it.paper.id })
            assertTrue(items.single().reasons.contains("Related to your saved papers"))
            db.dao().removeFavorite("2609.00101")
            assertTrue(repo.recommendations.first().isEmpty())
            repo.enhance()
            assertEquals(1, recommendationCalls) // no positive seeds means no online request
        } finally {
            db.close()
            if (before.categories.isNotEmpty()) prefs.setCategories(before.categories)
            prefs.setOnlineRecommendations(before.onlineRecommendations)
        }
    }
}
