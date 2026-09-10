package com.example.arxivpreview.data

import androidx.room.withTransaction
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.model.Paper
import kotlinx.coroutines.flow.map
import java.util.Locale

data class FavoriteSnapshot(val favorite: FavoriteEntity, val tags: List<FavoriteTagEntity>)
class FavoriteRepository(private val database: ArxivDatabase) {
    private val dao = database.dao()
    val records = dao.observeFavoriteRecords()
    val groups = dao.observeGroups()
    val tags = dao.observeTags()
    val relations = dao.observeFavoriteTags()
    fun observeFavorites() = dao.observeFavorites().map { list -> list.map { it.toModel() } }
    fun observeIsFavorite(id: String) = dao.observeIsFavorite(id)
    suspend fun setFavorite(paper: Paper, favorite: Boolean) = database.withTransaction {
        dao.upsertPaper(paper.toEntity())
        if (favorite) {
            if (dao.favorite(paper.id) == null) dao.addFavorite(FavoriteEntity(paper.id, System.currentTimeMillis()))
        } else dao.removeFavorite(paper.id)
    }
    suspend fun toggle(paper: Paper): FavoriteSnapshot? = database.withTransaction {
        val old = dao.favorite(paper.id)
        if (old == null) { setFavorite(paper, true); null }
        else FavoriteSnapshot(old, dao.favoriteTags(paper.id)).also { dao.removeFavorite(paper.id) }
    }
    suspend fun restore(snapshot: FavoriteSnapshot) = database.withTransaction {
        // A later explicit save wins over an older undo operation.
        if (dao.favorite(snapshot.favorite.paperId) == null) {
            dao.addFavorite(snapshot.favorite.copy(groupId = snapshot.favorite.groupId?.takeIf { dao.group(it) != null }))
            dao.addTags(snapshot.tags.filter { dao.tag(it.tagId) != null })
        }
    }
    suspend fun organize(paper: Paper, groupId: Long?, tagIds: Set<Long>) = database.withTransaction {
        require(groupId == null || dao.group(groupId) != null) { "This group no longer exists" }
        require(tagIds.all { dao.tag(it) != null }) { "A selected tag no longer exists" }
        setFavorite(paper, true)
        dao.addFavorite(dao.favorite(paper.id)!!.copy(groupId = groupId))
        dao.clearTags(paper.id)
        dao.addTags(tagIds.map { FavoriteTagEntity(paper.id, it) })
    }
    suspend fun saveGroup(id: Long = 0, value: String) = database.withTransaction {
        val name = validName(value); val key = name.lowercase(Locale.ROOT)
        require(!dao.groupNameExists(key, id)) { "This group name already exists" }
        require(id == 0L || dao.group(id) != null) { "This group no longer exists" }
        dao.saveGroup(FavoriteGroupEntity(id, name, key))
    }
    suspend fun saveTag(id: Long = 0, value: String) = database.withTransaction {
        val name = validName(value); val key = name.lowercase(Locale.ROOT)
        require(!dao.tagNameExists(key, id)) { "This tag name already exists" }
        require(id == 0L || dao.tag(id) != null) { "This tag no longer exists" }
        dao.saveTag(TagEntity(id, name, key))
    }
    suspend fun deleteGroup(id: Long) = database.withTransaction { dao.ungroup(id); dao.deleteGroup(id) }
    suspend fun deleteTag(id: Long) = dao.deleteTag(id)
    private fun validName(value: String): String = value.trim().also { require(it.isNotEmpty()) { "Name cannot be empty" } }
}
