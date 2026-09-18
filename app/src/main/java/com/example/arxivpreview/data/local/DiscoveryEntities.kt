package com.example.arxivpreview.data.local

import androidx.room.*

@Entity(tableName = "authors")
data class AuthorEntity(@PrimaryKey val id: String, val name: String, val affiliations: List<String>,
    val paperCount: Int?, val citationCount: Int?, val hIndex: Int?, val updatedAt: Long)
@Entity(tableName = "paper_authors", primaryKeys = ["paperId", "authorId"])
data class PaperAuthorEntity(val paperId: String, val authorId: String)
@Entity(tableName = "followed_authors")
data class FollowedAuthorEntity(@PrimaryKey val authorId: String, val followedAt: Long)
@Entity(tableName = "keywords")
data class KeywordEntity(@PrimaryKey val text: String, val createdAt: Long)
@Entity(tableName = "recommendation_feedback")
data class FeedbackEntity(@PrimaryKey val paperId: String, val createdAt: Long)
@Entity(tableName = "discovery_candidates", primaryKeys = ["paperId", "source"])
data class CandidateEntity(val paperId: String, val source: String, val discoveredAt: Long)
@Entity(tableName = "inbox")
data class InboxEntity(@PrimaryKey val paperId: String, val reasons: List<String>, val receivedAt: Long,
    val isRead: Boolean = false, val archived: Boolean = false)
@Entity(tableName = "discovery_sync")
data class SyncSourceEntity(@PrimaryKey val id: String, val windowStart: Long, val windowEnd: Long,
    val nextStart: Int = 0, val initialized: Boolean = false, val completedAt: Long = 0,
    val error: String? = null)
@Entity(tableName = "semantic_cache")
data class SemanticCacheEntity(@PrimaryKey val key: String, val payload: String, val fetchedAt: Long)
@Entity(tableName = "pending_author_papers", primaryKeys = ["paperId", "authorId"])
data class PendingAuthorPaperEntity(val paperId: String, val authorId: String, val since: Long, val silent: Boolean)

@Dao
interface DiscoveryDao {
    @Upsert suspend fun saveAuthor(author: AuthorEntity)
    @Query("SELECT * FROM authors WHERE id = :id") suspend fun author(id: String): AuthorEntity?
    @Query("SELECT authors.* FROM authors INNER JOIN followed_authors ON authors.id = followed_authors.authorId ORDER BY name")
    fun observeAuthors(): kotlinx.coroutines.flow.Flow<List<AuthorEntity>>
    @Upsert suspend fun mapAuthors(items: List<PaperAuthorEntity>)
    @Query("SELECT * FROM paper_authors") fun observeMappings(): kotlinx.coroutines.flow.Flow<List<PaperAuthorEntity>>
    @Query("SELECT * FROM paper_authors WHERE paperId = :id") suspend fun mappings(id: String): List<PaperAuthorEntity>
    @Upsert suspend fun follow(item: FollowedAuthorEntity)
    @Query("DELETE FROM followed_authors WHERE authorId = :id") suspend fun unfollow(id: String)
    @Query("SELECT * FROM followed_authors") suspend fun follows(): List<FollowedAuthorEntity>
    @Upsert suspend fun keyword(item: KeywordEntity)
    @Query("DELETE FROM keywords WHERE text = :text") suspend fun removeKeyword(text: String)
    @Query("SELECT * FROM keywords ORDER BY text") fun observeKeywords(): kotlinx.coroutines.flow.Flow<List<KeywordEntity>>
    @Upsert suspend fun dismiss(item: FeedbackEntity)
    @Query("DELETE FROM recommendation_feedback WHERE paperId = :id") suspend fun restore(id: String)
    @Query("SELECT * FROM recommendation_feedback ORDER BY createdAt DESC") fun observeFeedback(): kotlinx.coroutines.flow.Flow<List<FeedbackEntity>>
    @Upsert suspend fun candidates(items: List<CandidateEntity>)
    @Query("SELECT * FROM papers WHERE id IN (SELECT paperId FROM discovery_candidates UNION SELECT paperId FROM feed_items) ORDER BY publishedAt DESC")
    fun observeCandidates(): kotlinx.coroutines.flow.Flow<List<PaperEntity>>
    @Query("SELECT * FROM discovery_candidates") fun observeSources(): kotlinx.coroutines.flow.Flow<List<CandidateEntity>>
    @Query("SELECT * FROM inbox ORDER BY receivedAt DESC") fun observeInbox(): kotlinx.coroutines.flow.Flow<List<InboxEntity>>
    @Query("SELECT * FROM inbox WHERE paperId = :id") suspend fun inbox(id: String): InboxEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInbox(item: InboxEntity): Long
    @Query("UPDATE inbox SET reasons = :reasons WHERE paperId = :id") suspend fun reasons(id: String, reasons: List<String>)
    @Query("UPDATE inbox SET isRead = :read WHERE paperId = :id") suspend fun read(id: String, read: Boolean)
    @Query("UPDATE inbox SET archived = :archived WHERE paperId = :id") suspend fun archive(id: String, archived: Boolean)
    @Query("UPDATE inbox SET isRead = 1 WHERE archived = 0") suspend fun readAll()
    @Query("SELECT COUNT(*) FROM inbox WHERE isRead = 0 AND archived = 0") fun unreadCount(): kotlinx.coroutines.flow.Flow<Int>
    @Query("SELECT papers.* FROM papers INNER JOIN inbox ON papers.id = inbox.paperId") fun observeInboxPapers(): kotlinx.coroutines.flow.Flow<List<PaperEntity>>
    @Upsert suspend fun saveSync(source: SyncSourceEntity)
    @Query("SELECT * FROM discovery_sync WHERE id = :id") suspend fun sync(id: String): SyncSourceEntity?
    @Query("SELECT * FROM discovery_sync") fun observeSync(): kotlinx.coroutines.flow.Flow<List<SyncSourceEntity>>
    @Query("DELETE FROM discovery_sync WHERE id = :id") suspend fun deleteSync(id: String)
    @Upsert suspend fun cache(cache: SemanticCacheEntity)
    @Query("SELECT * FROM semantic_cache WHERE `key` = :key") suspend fun cache(key: String): SemanticCacheEntity?
    @Query("DELETE FROM semantic_cache") suspend fun clearCache()
    @Upsert suspend fun pending(item: PendingAuthorPaperEntity)
    @Query("SELECT * FROM pending_author_papers") suspend fun pending(): List<PendingAuthorPaperEntity>
    @Query("DELETE FROM pending_author_papers WHERE paperId = :paperId AND authorId = :authorId") suspend fun clearPending(paperId: String, authorId: String)
}
