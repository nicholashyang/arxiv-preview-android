package com.example.arxivpreview.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.arxivpreview.model.Paper

@Entity(tableName = "papers")
data class PaperEntity(
    @PrimaryKey val id: String,
    val versionedId: String,
    val title: String,
    val summary: String,
    val authors: List<String>,
    val publishedAt: Long,
    val updatedAt: Long,
    val primaryCategory: String,
    val categories: List<String>,
    val abstractUrl: String,
    val pdfUrl: String,
    val doi: String?,
    val journalReference: String?,
)

@Entity(
    tableName = "feed_items",
    primaryKeys = ["paperId"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paperId"), Index("position")],
)
data class FeedItemEntity(
    val paperId: String,
    val position: Int,
    val cachedAt: Long,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paperId")],
)
data class FavoriteEntity(
    @PrimaryKey val paperId: String,
    val createdAt: Long,
    val groupId: Long? = null,
)

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paperId")],
)
data class DownloadEntity(
    @PrimaryKey val paperId: String,
    val versionedId: String,
    val filePath: String,
    val bytes: Long,
    val downloadedAt: Long,
)

fun Paper.toEntity() = PaperEntity(
    id = id,
    versionedId = versionedId,
    title = title,
    summary = summary,
    authors = authors,
    publishedAt = publishedAt,
    updatedAt = updatedAt,
    primaryCategory = primaryCategory,
    categories = categories,
    abstractUrl = abstractUrl,
    pdfUrl = pdfUrl,
    doi = doi,
    journalReference = journalReference,
)

fun PaperEntity.toModel() = Paper(
    id = id,
    versionedId = versionedId,
    title = title,
    summary = summary,
    authors = authors,
    publishedAt = publishedAt,
    updatedAt = updatedAt,
    primaryCategory = primaryCategory,
    categories = categories,
    abstractUrl = abstractUrl,
    pdfUrl = pdfUrl,
    doi = doi,
    journalReference = journalReference,
)

@Entity(tableName = "favorite_groups", indices = [Index(value = ["nameKey"], unique = true)])
data class FavoriteGroupEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val nameKey: String)

@Entity(tableName = "tags", indices = [Index(value = ["nameKey"], unique = true)])
data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val nameKey: String)

@Entity(tableName = "favorite_tags", primaryKeys = ["paperId", "tagId"],
    foreignKeys = [ForeignKey(entity = FavoriteEntity::class, parentColumns = ["paperId"], childColumns = ["paperId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tagId")])
data class FavoriteTagEntity(val paperId: String, val tagId: Long)

@Entity(tableName = "html_progress")
data class HtmlProgressEntity(@PrimaryKey val versionedId: String, val anchor: String, val offset: Double, val ratio: Double)
