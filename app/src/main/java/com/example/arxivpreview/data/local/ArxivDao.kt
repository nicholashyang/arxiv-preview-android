package com.example.arxivpreview.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArxivDao {
    @Upsert
    suspend fun upsertPapers(papers: List<PaperEntity>)

    @Upsert
    suspend fun upsertPaper(paper: PaperEntity)

    @Query("SELECT * FROM papers WHERE id = :id")
    fun observePaper(id: String): Flow<PaperEntity?>

    @Query("SELECT * FROM papers WHERE id = :id")
    suspend fun getPaper(id: String): PaperEntity?

    @Query(
        """
        SELECT papers.* FROM papers
        INNER JOIN feed_items ON papers.id = feed_items.paperId
        ORDER BY feed_items.position ASC
        """,
    )
    fun observeFeed(): Flow<List<PaperEntity>>

    @Query("SELECT COUNT(*) FROM feed_items")
    suspend fun feedCount(): Int

    @Query("DELETE FROM feed_items")
    suspend fun clearFeed()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItems(items: List<FeedItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE paperId = :paperId")
    suspend fun removeFavorite(paperId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE paperId = :paperId)")
    fun observeIsFavorite(paperId: String): Flow<Boolean>

    @Query(
        """
        SELECT papers.* FROM papers
        INNER JOIN favorites ON papers.id = favorites.paperId
        ORDER BY favorites.createdAt DESC
        """,
    )
    fun observeFavorites(): Flow<List<PaperEntity>>

    @Upsert
    suspend fun upsertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE paperId = :paperId")
    fun observeDownload(paperId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE paperId = :paperId")
    suspend fun getDownload(paperId: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE paperId = :paperId")
    suspend fun removeDownload(paperId: String)
}
