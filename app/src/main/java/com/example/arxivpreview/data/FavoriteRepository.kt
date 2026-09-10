package com.example.arxivpreview.data

import com.example.arxivpreview.data.local.ArxivDao
import com.example.arxivpreview.data.local.FavoriteEntity
import com.example.arxivpreview.data.local.toEntity
import com.example.arxivpreview.data.local.toModel
import com.example.arxivpreview.model.Paper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteRepository(private val dao: ArxivDao) {
    fun observeFavorites(): Flow<List<Paper>> =
        dao.observeFavorites().map { papers -> papers.map { it.toModel() } }

    fun observeIsFavorite(paperId: String): Flow<Boolean> = dao.observeIsFavorite(paperId)

    suspend fun setFavorite(paper: Paper, favorite: Boolean) {
        dao.upsertPaper(paper.toEntity())
        if (favorite) {
            dao.addFavorite(FavoriteEntity(paper.id, System.currentTimeMillis()))
        } else {
            dao.removeFavorite(paper.id)
        }
    }
}
