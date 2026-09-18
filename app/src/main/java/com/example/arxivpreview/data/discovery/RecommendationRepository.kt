package com.example.arxivpreview.data.discovery

import com.example.arxivpreview.data.*
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import java.util.Locale

object RecommendationRanker {
    fun tokens(text: String): Set<String> = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 2 && it !in stopWords }.toSet()
    fun similarity(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size / sqrt(a.size.toDouble() * b.size)
    fun keywordMatches(paper: Paper, keyword: String): Boolean =
        (paper.title + " " + paper.summary).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").contains(keyword.lowercase(Locale.ROOT))
    fun rank(papers: List<Paper>, categories: Set<String>, keywords: List<String>, authorPapers: Set<String>,
        favorites: List<Paper>, excluded: Set<String>, online: Set<String>, now: Long): List<RecommendationItem> {
        val favoriteIds = favorites.map { ArxivIds.normalize(it.id) }.toSet()
        val favoriteTerms = favorites.take(20).map { tokens(it.title + " " + it.summary) }
        val scored = papers.groupBy { ArxivIds.normalize(it.id) }.values.map { versions -> versions.maxBy { it.updatedAt } }
            .filter { ArxivIds.normalize(it.id) !in favoriteIds && ArxivIds.normalize(it.id) !in excluded }.map { paper ->
                val reasons = mutableListOf<String>()
                val matching = keywords.filter { keywordMatches(paper, it) }
                if (matching.isNotEmpty()) reasons += "Matches your topic: ${matching.take(2).joinToString(", ")}"
                if (paper.id in authorPapers) reasons += "From an author you follow"
                val category = paper.categories.firstOrNull { it in categories }
                if (category != null) reasons += "In your category: $category"
                val related = favoriteTerms.maxOfOrNull { similarity(tokens(paper.title + " " + paper.summary), it) } ?: 0.0
                if (related >= .12 || paper.id in online) reasons += "Related to your saved papers"
                val age = ((now - paper.publishedAt).coerceAtLeast(0) / 86_400_000.0)
                val freshness = exp(-age / 7)
                val score = (if (matching.isNotEmpty()) .35 else 0.0) + (if (paper.id in authorPapers) .25 else 0.0) +
                    (if (category != null) .20 else 0.0) + related * .10 + freshness * .10 + (if (paper.id in online) .10 else 0.0)
                if (reasons.isEmpty()) reasons += "Recent research to explore"
                RecommendationItem(paper, reasons, score, paper.publishedAt < now - 7 * 86_400_000L)
            }
        // Greedy category penalty provides diversity without mixing old papers into the recent section.
        return listOf(false, true).flatMap { earlier ->
            val remaining = scored.filter { it.earlier == earlier }.sortedWith(compareByDescending<RecommendationItem> { it.score }.thenBy { it.paper.id }).toMutableList()
            val result = mutableListOf<RecommendationItem>()
            val counts = mutableMapOf<String, Int>()
            while (remaining.isNotEmpty()) {
                val next = remaining.maxBy { it.score - .08 * (counts[it.paper.primaryCategory] ?: 0) }
                result += next; remaining.remove(next)
                counts[next.paper.primaryCategory] = (counts[next.paper.primaryCategory] ?: 0) + 1
            }
            result
        }
    }
    private val stopWords = setOf("the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "our", "we", "paper", "study", "using", "their", "these", "which", "can", "has", "have", "into", "not", "also")
}

class RecommendationRepository(private val database: ArxivDatabase, private val preferences: PreferencesRepository,
    private val papers: PaperRepository, private val api: SemanticScholarClient) {
    private val dao = database.discoveryDao()
    private data class Signals(val keywords: List<String>, val authors: Set<String>, val feedback: Set<String>, val mappings: List<PaperAuthorEntity>, val sources: List<CandidateEntity>)
    private val signals = combine(dao.observeKeywords(), dao.observeAuthors(), dao.observeFeedback(), dao.observeMappings(), dao.observeSources()) { k,a,f,m,s ->
        Signals(k.map { it.text }, a.map { it.id }.toSet(), f.map { it.paperId }.toSet(), m, s)
    }
    val recommendations: Flow<List<RecommendationItem>> = combine(dao.observeCandidates(), database.dao().observeFavorites(), preferences.preferences, signals) { candidates, favorites, prefs, s ->
        val authorPapers = s.mappings.filter { it.authorId in s.authors }.map { it.paperId }.toSet()
        val active = candidates.map { it.toModel() }.filter { p ->
            p.categories.any { it in prefs.categories } || s.keywords.any { RecommendationRanker.keywordMatches(p, it) } || p.id in authorPapers || (prefs.onlineRecommendations && favorites.isNotEmpty() && s.sources.any { it.paperId == p.id && it.source == onlineSource(favorites.map { p -> p.id }, s.feedback) })
        }
        RecommendationRanker.rank(active, prefs.categories, s.keywords, authorPapers, favorites.map { it.toModel() }, s.feedback,
            s.sources.filter { prefs.onlineRecommendations && favorites.isNotEmpty() && it.source == onlineSource(favorites.map { p -> p.id }, s.feedback) }.map { it.paperId }.toSet(), System.currentTimeMillis())
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    private fun onlineSource(favorites: List<String>, excluded: Set<String>): String {
        val input = favorites.take(20).sorted().joinToString(",") + "|" + excluded.sorted().joinToString(",")
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        return "online:$hash"
    }
    suspend fun dismiss(id: String) = dao.dismiss(FeedbackEntity(ArxivIds.normalize(id), System.currentTimeMillis()))
    suspend fun restore(id: String) = dao.restore(id)
    private suspend fun semanticId(id: String): String? = try {
        api.get("graph/v1/paper/ARXIV:${ArxivIds.normalize(id)}", mapOf("fields" to "externalIds")).payload.stringOrNull("paperId")
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    suspend fun enhance() {
        if (!preferences.preferences.first().onlineRecommendations) return
        val favorites = database.dao().observeFavoriteRecords().first().take(20).map { it.paperId }
        val excluded = dao.observeFeedback().first()
        val source = onlineSource(favorites, excluded.map { it.paperId }.toSet())
        val positive = favorites.mapNotNull { semanticId(it) }
        if (positive.isEmpty()) return
        val negative = excluded.take(20).mapNotNull { semanticId(it.paperId) }
        val body = JSONObject().put("positivePaperIds", JSONArray(positive)).put("negativePaperIds", JSONArray(negative)).toString()
        val json = api.get("recommendations/v1/papers", mapOf("fields" to "externalIds,title", "limit" to "50"), body, SemanticScholarClient.RECOMMENDATION_TTL).payload
        hydrate(json, source)
    }
    private suspend fun hydrate(json: JSONObject, source: String): List<Paper> {
        val result = mutableListOf<Paper>()
        for (work in json.optJSONArray("recommendedPapers")?.objects().orEmpty().mapNotNull(AuthorRepository::work)) {
            val id = work.arxivId ?: continue
            try {
                val p = papers.getPaper(id) ?: papers.refreshPaper(id) ?: continue
                dao.candidates(listOf(CandidateEntity(p.id, source, System.currentTimeMillis())))
                result += p
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* A failed paper does not discard other results. */ }
        }
        return result
    }
    suspend fun similar(paper: Paper): List<Paper> {
        if (preferences.preferences.first().onlineRecommendations) {
            try {
                val id = semanticId(paper.id)
                if (id != null) {
                    val json = api.get("recommendations/v1/papers/forpaper/$id", mapOf("fields" to "externalIds,title", "limit" to "10"), ttl = SemanticScholarClient.RECOMMENDATION_TTL).payload
                    val result = hydrate(json, "similar:${paper.id}").filter { it.id != paper.id }
                    if (result.isNotEmpty()) return result
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        val terms = RecommendationRanker.tokens(paper.title + " " + paper.summary)
        return dao.observeCandidates().first().map { it.toModel() }.filter { it.id != paper.id }
            .map { it to RecommendationRanker.similarity(terms, RecommendationRanker.tokens(it.title + " " + it.summary)) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(10).map { it.first }
    }
}
