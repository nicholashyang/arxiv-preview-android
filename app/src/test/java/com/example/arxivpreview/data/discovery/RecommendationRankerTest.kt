package com.example.arxivpreview.data.discovery

import com.example.arxivpreview.model.Paper
import org.junit.Assert.*
import org.junit.Test

class RecommendationRankerTest {
    private val now = 1_800_000_000_000L
    private fun paper(id: String, category: String = "hep-ph", age: Long = 0, title: String = "Parton distributions at small x") =
        Paper(id, "${id}v1", title, "Evolution of double parton distributions", listOf("Same Name"), now - age * 86_400_000, now, category, listOf(category), "", "", null, null)
    @Test fun coldStartUsesCategoriesAndRecencyWithReasons() {
        val recent = paper("2609.00001"); val old = paper("2609.00002", age = 12)
        val items = RecommendationRanker.rank(listOf(old, recent), setOf("hep-ph"), emptyList(), emptySet(), emptyList(), emptySet(), emptySet(), now)
        assertEquals(recent.id, items.first().paper.id)
        assertTrue(items.first().reasons.contains("In your category: hep-ph"))
        assertFalse(items.first().earlier); assertTrue(items.last().earlier)
    }
    @Test fun deduplicatesVersionsAndExcludesFavoritesAndNegativeFeedback() {
        val saved = paper("2609.00001"); val hidden = paper("2609.00002"); val visible = paper("2609.00003")
        val newer = visible.copy(versionedId = "${visible.id}v2", updatedAt = now + 1)
        val items = RecommendationRanker.rank(listOf(saved, hidden, visible, newer), emptySet(), emptyList(), emptySet(), listOf(saved), setOf(hidden.id), emptySet(), now)
        assertEquals(listOf(newer), items.map { it.paper })
        val restored = RecommendationRanker.rank(listOf(saved, hidden), emptySet(), emptyList(), emptySet(), emptyList(), emptySet(), emptySet(), now)
        assertEquals(2, restored.size)
    }
    @Test fun explicitAuthorIdentityAndTopicsWorkAcrossCategories() {
        val a = paper("2609.00001", "math.SG"); val b = paper("2609.00002", "cs.AI", title = "Unrelated problem")
        val items = RecommendationRanker.rank(listOf(a,b), emptySet(), listOf("double parton"), setOf(a.id), emptyList(), emptySet(), emptySet(), now)
        assertEquals(a.id, items.first().paper.id)
        assertTrue(items.first().reasons.contains("From an author you follow"))
        assertFalse(items.last().reasons.contains("From an author you follow")) // same name does not imply identity
    }
    @Test fun diversityBreaksUpRepeatedCategoriesWithoutMovingOldPapersForward() {
        val papers = (1..4).map { paper("2609.0000$it") } + paper("2609.00005", "math.SG") + paper("2609.00006", "math.SG", 20)
        val items = RecommendationRanker.rank(papers, setOf("hep-ph", "math.SG"), emptyList(), emptySet(), emptyList(), emptySet(), emptySet(), now)
        assertEquals("math.SG", items[1].paper.primaryCategory)
        assertTrue(items.last().earlier)
    }
    @Test fun semanticUrlsEncodeLegacyIdsAsOnePathSegment() {
        val url = OkHttpSemanticTransport.buildUrl("graph/v1/paper/ARXIV:hep-th/9901001", mapOf("fields" to "authors"))
        assertEquals("ARXIV:hep-th/9901001", url.pathSegments.last())
        assertTrue(url.encodedPath.contains("hep-th%2F9901001"))
        val search = OkHttpSemanticTransport.buildUrl("graph/v1/author/search", mapOf("query" to "A & B"))
        assertEquals("A & B", search.queryParameter("query"))
    }
    @Test fun emptyTermsAndMissingDatesDoNotProduceInvalidScores() {
        val item = RecommendationRanker.rank(listOf(paper("2609.00001").copy(title = "", summary = "", publishedAt = 0)), emptySet(), emptyList(), emptySet(), emptyList(), emptySet(), emptySet(), now).single()
        assertTrue(item.score.isFinite()); assertTrue(item.earlier)
        assertEquals(0.0, RecommendationRanker.similarity(emptySet(), setOf("word")), 0.0)
    }
}
