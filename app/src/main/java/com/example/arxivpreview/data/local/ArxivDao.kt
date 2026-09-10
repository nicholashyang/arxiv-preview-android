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

    @Upsert
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

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeFavoriteRecords(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites WHERE paperId = :id")
    suspend fun favorite(id: String): FavoriteEntity?
    @Query("SELECT * FROM favorite_groups ORDER BY nameKey")
    fun observeGroups(): Flow<List<FavoriteGroupEntity>>
    @Query("SELECT * FROM tags ORDER BY nameKey")
    fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM favorite_tags")
    fun observeFavoriteTags(): Flow<List<FavoriteTagEntity>>
    @Query("SELECT * FROM favorite_tags WHERE paperId = :id")
    suspend fun favoriteTags(id: String): List<FavoriteTagEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_groups WHERE nameKey = :key AND id != :exceptId)") suspend fun groupNameExists(key: String, exceptId: Long): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE nameKey = :key AND id != :exceptId)") suspend fun tagNameExists(key: String, exceptId: Long): Boolean
    @Upsert suspend fun saveGroup(group: FavoriteGroupEntity)
    @Upsert suspend fun saveTag(tag: TagEntity)
    @Query("UPDATE favorites SET groupId = NULL WHERE groupId = :id")
    suspend fun ungroup(id: Long)
    @Query("DELETE FROM favorite_groups WHERE id = :id") suspend fun deleteGroup(id: Long)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: Long)
    @Query("DELETE FROM favorite_tags WHERE paperId = :id") suspend fun clearTags(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTags(tags: List<FavoriteTagEntity>)
    @Query("SELECT * FROM favorite_groups WHERE id = :id") suspend fun group(id: Long): FavoriteGroupEntity?
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: Long): TagEntity?
    @Upsert suspend fun saveProgress(progress: HtmlProgressEntity)
    @Query("SELECT * FROM html_progress WHERE versionedId = :id") suspend fun progress(id: String): HtmlProgressEntity?
}
