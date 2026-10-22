package com.example.arxivpreview.data.discovery

import androidx.room.withTransaction
import com.example.arxivpreview.data.*
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DiscoverySyncCoordinator(private val db: ArxivDatabase, private val preferences: PreferencesRepository,
    private val papers: PaperRepository, private val authors: AuthorRepository, private val recommendations: RecommendationRepository,
    private val now: () -> Long = System::currentTimeMillis) {
    data class Result(val newCount: Int, val pending: Boolean, val errors: List<String>)
    data class State(val running: Boolean = false, val message: String? = null)
    private data class Source(val id: String, val reason: String, val query: String?, val authorId: String? = null)
    val state = MutableStateFlow(State())
    private val gate = Mutex()
    private val dao = db.discoveryDao()
    suspend fun refresh(): Result = gate.withLock {
        state.value = State(true)
        val inserted = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        var unfinished = false
        try {
            val prefs = preferences.preferences.first()
            val followed = dao.observeAuthors().first()
            val sources = prefs.categories.sorted().map { Source("category:$it", "Category: $it", "cat:$it") } +
                dao.observeKeywords().first().map { Source("keyword:${it.text}", "Topic: ${it.text}", "(ti:${quote(it.text)} OR abs:${quote(it.text)})") } +
                followed.map { Source("author:${it.id}", "Author: ${it.name}", null, it.id) }
            for (source in sources) {
                state.value = State(true, "Refreshing ${source.reason}…")
                currentCoroutineContext().ensureActive()
                val previous = dao.sync(source.id)
                var cursor = if (previous == null || previous.windowEnd == 0L) {
                    SyncSourceEntity(source.id, (previous?.completedAt?.minus(2 * DAY) ?: (now() - 7 * DAY)), now(),
                        initialized = previous?.initialized ?: false, completedAt = previous?.completedAt ?: 0)
                } else previous
                dao.saveSync(cursor.copy(error = null))
                try {
                    // Persist every page. Large subscriptions resume on the next refresh/worker run.
                    repeat(8) {
                        if (cursor.windowEnd == 0L) return@repeat
                        val next: Int?
                        if (source.authorId != null) {
                            val year = Instant.ofEpochMilli(cursor.windowStart - 30 * DAY).atZone(ZoneOffset.UTC).year
                            val page = authors.works(source.authorId, cursor.nextStart, year, fresh = true)
                            for (work in page.papers) {
                                val id = work.arxivId ?: continue
                                try {
                                    val paper = papers.getPaper(id) ?: papers.refreshPaper(id) ?: throw IllegalStateException("arXiv metadata unavailable")
                                    dao.mapAuthors(listOf(PaperAuthorEntity(id, source.authorId)))
                                    if (paper.publishedAt >= (if (cursor.initialized) cursor.windowStart - 30 * DAY else cursor.windowStart) && paper.publishedAt <= cursor.windowEnd) {
                                        if (ingest(paper, source.id, source.reason, !cursor.initialized)) inserted += paper.id
                                    }
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { dao.pending(PendingAuthorPaperEntity(id, source.authorId, cursor.windowStart, !cursor.initialized)) }
                            }
                            next = page.next
                        } else {
                            val query = "${source.query} AND submittedDate:[${date(cursor.windowStart)} TO ${date(cursor.windowEnd)}]"
                            val page = papers.remote.search(query, cursor.nextStart, 50)
                            for (paper in page.papers) {
                                val keyword = source.id.takeIf { it.startsWith("keyword:") }?.removePrefix("keyword:")
                                if (keyword != null && !RecommendationRanker.keywordMatches(paper, keyword)) continue
                                if (ingest(paper, source.id, source.reason, !cursor.initialized)) inserted += paper.id
                            }
                            next = (cursor.nextStart + page.papers.size).takeIf { it < page.totalResults && page.papers.isNotEmpty() }
                            if (page.papers.isEmpty() && cursor.nextStart < page.totalResults) throw IllegalStateException("Incomplete page; retry to continue")
                        }
                        cursor = if (next != null) cursor.copy(nextStart = next, error = null)
                        else cursor.copy(windowEnd = 0, nextStart = 0, initialized = true, completedAt = cursor.windowEnd, error = null)
                        dao.saveSync(cursor)
                    }
                    if (cursor.windowEnd != 0L) unfinished = true
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val message = "${source.reason}: ${e.message ?: "Could not refresh"}"
                    errors += message
                    dao.saveSync(cursor.copy(error = message))
                }
            }
            for (pending in dao.pending()) {
                val author = followed.firstOrNull { it.id == pending.authorId }
                if (author == null) { dao.clearPending(pending.paperId, pending.authorId); continue }
                try {
                    val paper = papers.getPaper(pending.paperId) ?: papers.refreshPaper(pending.paperId) ?: continue
                    dao.mapAuthors(listOf(PaperAuthorEntity(paper.id, author.id)))
                    if (paper.publishedAt >= pending.since && ingest(paper, "author:${author.id}", "Author: ${author.name}", pending.silent)) inserted += paper.id
                    dao.clearPending(paper.id, author.id)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { unfinished = true }
            }
            if (dao.pending().isNotEmpty()) unfinished = true
            // Seed offline ranking with previously cached Latest items on an upgrade.
            val cachedLatest = papers.observeLatest().first()
            dao.candidates(cachedLatest.map { CandidateEntity(it.id, "cached", now()) })
            try { recommendations.enhance() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors += (e.message ?: "Online recommendations unavailable") }
            val message = errors.firstOrNull() ?: if (unfinished) "More updates are available. Refresh to continue." else null
            state.value = State(false, message)
            Result(inserted.size, unfinished || errors.isNotEmpty(), errors)
        } finally { state.update { if (it.running) State() else it } }
    }
    /** Insert once, then only merge reasons. Read/archive flags belong to the user. */
    internal suspend fun ingest(paper: Paper, source: String, reason: String, silent: Boolean): Boolean = db.withTransaction {
        db.dao().upsertPaper(paper.toEntity())
        dao.candidates(listOf(CandidateEntity(paper.id, source, now())))
        val existing = dao.inbox(paper.id)
        if (existing == null) {
            val inserted = dao.insertInbox(InboxEntity(paper.id, listOf(reason), now())) != -1L
            inserted && !silent
        } else {
            dao.reasons(paper.id, (existing.reasons + reason).distinct())
            false
        }
    }
    companion object {
        const val DAY = 86_400_000L
        fun quote(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        private fun date(value: Long): String = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(value))
    }
}

class InboxRepository(private val dao: DiscoveryDao) {
    val unread = dao.unreadCount()
    val items: Flow<List<InboxItem>> = combine(dao.observeInbox(), dao.observeInboxPapers()) { records, papers ->
        val byId = papers.associateBy { it.id }
        records.mapNotNull { r -> byId[r.paperId]?.let { InboxItem(it.toModel(), r.reasons, r.receivedAt, r.isRead, r.archived) } }
    }
    val unreadByCategory: Flow<Map<String, Int>> = items.map { inbox ->
        inbox.asSequence()
            .filter { !it.isRead && !it.archived }
            .flatMap { it.paper.categories.distinct().asSequence() }
            .groupingBy { it }.eachCount()
    }
    suspend fun read(id: String, read: Boolean = true) = dao.read(id, read)
    suspend fun archive(id: String, archived: Boolean) = dao.archive(id, archived)
    suspend fun readAll() = dao.readAll()
}
