package com.example.arxivpreview.data.discovery

import com.example.arxivpreview.data.PaperRepository
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONArray

internal fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
internal fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

class AuthorRepository(private val dao: DiscoveryDao, private val api: SemanticScholarClient, private val papers: PaperRepository) {
    val followed = dao.observeAuthors()
    suspend fun search(query: String, offset: Int = 0): Pair<List<AuthorSummary>, Int?> {
        require(query.isNotBlank())
        val json = api.get("graph/v1/author/search", mapOf("query" to query.trim(), "offset" to "$offset", "limit" to "20", "fields" to "name")).payload
        return json.optJSONArray("data")?.objects().orEmpty().mapNotNull(::summary) to json.nullableInt("next")
    }
    suspend fun paperAuthors(paperId: String): List<AuthorSummary> {
        val response = api.get("graph/v1/paper/ARXIV:${ArxivIds.normalize(paperId)}", mapOf("fields" to "authors"))
        val authors = response.payload.optJSONArray("authors")?.objects().orEmpty().mapNotNull(::summary)
        dao.mapAuthors(authors.map { PaperAuthorEntity(ArxivIds.normalize(paperId), it.id) })
        return authors
    }
    suspend fun resolve(paperId: String, name: String): List<AuthorSummary> {
        val authors = try { paperAuthors(paperId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        // A name is never an identity. These are selectable candidates from the actual paper.
        return authors.sortedByDescending { it.name.equals(name, ignoreCase = true) }
    }
    suspend fun profile(id: String, force: Boolean = false): AuthorProfile {
        try {
            val response = api.get("graph/v1/author/$id", mapOf("fields" to "name,affiliations,paperCount,citationCount,hIndex"), force = force)
            val p = response.payload
            val affiliations = p.optJSONArray("affiliations")
            return AuthorEntity(id, p.optString("name"), (0 until (affiliations?.length() ?: 0)).map { affiliations!!.getString(it) },
                p.nullableInt("paperCount"), p.nullableInt("citationCount"), p.nullableInt("hIndex"), response.fetchedAt).also { dao.saveAuthor(it) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return dao.author(id) ?: throw e }
    }
    suspend fun follow(id: String, enabled: Boolean) {
        if (enabled) {
            if (dao.follows().any { it.authorId == id }) return
            profile(id)
            dao.follow(FollowedAuthorEntity(id, System.currentTimeMillis()))
        } else {
            dao.unfollow(id)
            dao.deleteSync("author:$id")
        }
    }
    suspend fun works(id: String, offset: Int = 0, sinceYear: Int? = null, fresh: Boolean = false): AuthorPage {
        val params = mutableMapOf("offset" to "$offset", "limit" to "50", "fields" to "title,externalIds,year,publicationDate,url")
        if (sinceYear != null) params["publicationDateOrYear"] = "$sinceYear:"
        val response = api.get("graph/v1/author/$id/papers", params, force = fresh, allowStale = !fresh)
        val data = response.payload
        return AuthorPage(data.optJSONArray("data")?.objects().orEmpty().mapNotNull(::work), data.nullableInt("next"), response.fetchedAt, response.stale)
    }
    suspend fun open(work: AuthorWork): Paper? = work.arxivId?.let { papers.getPaper(it) ?: papers.refreshPaper(it) }
    companion object {
        fun summary(p: JSONObject): AuthorSummary? = p.stringOrNull("authorId")?.let { AuthorSummary(it, p.optString("name", "Unknown author")) }
        fun work(p: JSONObject): AuthorWork? = p.stringOrNull("paperId")?.let { id ->
            val arxiv = p.optJSONObject("externalIds")?.stringOrNull("ArXiv")?.let(ArxivIds::normalize)?.takeIf(String::isNotBlank)
            AuthorWork(id, p.optString("title", "Untitled"), arxiv, p.nullableInt("year"), p.stringOrNull("publicationDate"), "https://www.semanticscholar.org/paper/$id")
        }
    }
}
