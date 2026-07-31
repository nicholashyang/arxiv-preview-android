package com.example.arxivpreview.data

import androidx.room.withTransaction
import com.example.arxivpreview.data.local.ArxivDao
import com.example.arxivpreview.data.local.ArxivDatabase
import com.example.arxivpreview.data.local.FeedItemEntity
import com.example.arxivpreview.data.local.toEntity
import com.example.arxivpreview.data.local.toModel
import com.example.arxivpreview.data.remote.ArxivRemoteDataSource
import com.example.arxivpreview.model.Paper
import com.example.arxivpreview.model.PaperPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PaperRepository(
    private val database: ArxivDatabase,
    private val dao: ArxivDao,
    private val remote: ArxivRemoteDataSource,
) {
    fun observeLatest(): Flow<List<Paper>> =
        dao.observeFeed().map { items -> items.map { it.toModel() } }

    fun observePaper(id: String): Flow<Paper?> =
        dao.observePaper(id).map { it?.toModel() }

    suspend fun getPaper(id: String): Paper? = dao.getPaper(id)?.toModel()

    suspend fun refreshLatest(categories: Set<String>, append: Boolean = false): PaperPage {
        val start = if (append) dao.feedCount() else 0
        val page = remote.search(categoryQuery(categories), start)
        database.withTransaction {
            if (!append) dao.clearFeed()
            dao.upsertPapers(page.papers.map(Paper::toEntity))
            dao.insertFeedItems(
                page.papers.mapIndexed { index, paper ->
                    FeedItemEntity(
                        paperId = paper.id,
                        position = start + index,
                        cachedAt = System.currentTimeMillis(),
                    )
                },
            )
        }
        return page
    }

    suspend fun search(term: String, category: String?, start: Int): PaperPage {
        val page = remote.search(searchQuery(term, category), start, sortBy = "relevance")
        dao.upsertPapers(page.papers.map(Paper::toEntity))
        return page
    }

    suspend fun refreshPaper(id: String): Paper? {
        val page = remote.search("id:$id", 0, 1, sortBy = "lastUpdatedDate")
        page.papers.firstOrNull()?.let { dao.upsertPaper(it.toEntity()) }
        return page.papers.firstOrNull()
    }

    suspend fun replaceLatest(papers: List<Paper>) {
        database.withTransaction {
            dao.clearFeed()
            dao.upsertPapers(papers.map(Paper::toEntity))
            dao.insertFeedItems(
                papers.mapIndexed { index, paper ->
                    FeedItemEntity(paper.id, index, System.currentTimeMillis())
                },
            )
        }
    }

    suspend fun fetchDaily(categories: Set<String>): List<Paper> =
        remote.search(categoryQuery(categories), 0, 50).papers

    companion object {
        fun categoryQuery(categories: Set<String>): String =
            categories.sorted().joinToString(" OR ", prefix = "(", postfix = ")") { "cat:$it" }

        fun searchQuery(term: String, category: String?): String {
            val safe = term.trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            val base = "all:\"$safe\""
            return if (category.isNullOrBlank()) base else "$base AND cat:$category"
        }
    }
}
