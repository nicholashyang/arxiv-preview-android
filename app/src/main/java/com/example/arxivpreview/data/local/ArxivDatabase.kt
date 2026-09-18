package com.example.arxivpreview.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AuthorEntity::class, PaperAuthorEntity::class, FollowedAuthorEntity::class, KeywordEntity::class,
        FeedbackEntity::class, CandidateEntity::class, InboxEntity::class, SyncSourceEntity::class, SemanticCacheEntity::class, PendingAuthorPaperEntity::class,
        PaperEntity::class,
        FeedItemEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
        FavoriteGroupEntity::class, TagEntity::class, FavoriteTagEntity::class, HtmlProgressEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ArxivDatabase : RoomDatabase() {
    abstract fun dao(): ArxivDao
    abstract fun discoveryDao(): DiscoveryDao
}

/** Repair legacy IDs atomically, carrying every user-owned relation to the canonical key. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        data class Repair(val old: String, val id: String, val version: String)
        val repairs = mutableListOf<Repair>()
        db.query("SELECT id, versionedId, abstractUrl, pdfUrl FROM papers").use { rows ->
            while (rows.moveToNext()) {
                val version = com.example.arxivpreview.model.ArxivIds.versioned(rows.getString(1))
                    .ifBlank { com.example.arxivpreview.model.ArxivIds.versioned(rows.getString(2)) }
                    .ifBlank { com.example.arxivpreview.model.ArxivIds.versioned(rows.getString(3)) }
                val id = com.example.arxivpreview.model.ArxivIds.normalize(version)
                if (id.isNotBlank() && id != rows.getString(0)) repairs += Repair(rows.getString(0), id, version)
            }
        }
        repairs.forEach { r ->
            db.execSQL("""INSERT OR IGNORE INTO papers
                SELECT ?, ?, title, summary, authors, publishedAt, updatedAt, primaryCategory,
                categories, abstractUrl, pdfUrl, doi, journalReference FROM papers WHERE id = ?""",
                arrayOf(r.id, r.version, r.old))
            db.execSQL("INSERT OR IGNORE INTO favorites SELECT ?, createdAt FROM favorites WHERE paperId = ?", arrayOf(r.id, r.old))
            db.execSQL("INSERT OR IGNORE INTO downloads SELECT ?, ?, filePath, bytes, downloadedAt FROM downloads WHERE paperId = ?", arrayOf(r.id, r.version, r.old))
            db.execSQL("INSERT OR IGNORE INTO feed_items SELECT ?, position, cachedAt FROM feed_items WHERE paperId = ?", arrayOf(r.id, r.old))
            // Room enables foreign keys on open; migrations must not rely on cascading deletes.
            for (table in listOf("favorites", "downloads", "feed_items")) {
                db.execSQL("DELETE FROM $table WHERE paperId = ?", arrayOf(r.old))
            }
            db.execSQL("DELETE FROM papers WHERE id = ?", arrayOf(r.old))
        }
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN groupId INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS favorite_groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_groups_nameKey ON favorite_groups (nameKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_nameKey ON tags (nameKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS favorite_tags (paperId TEXT NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(paperId, tagId), FOREIGN KEY(paperId) REFERENCES favorites(paperId) ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_tags_tagId ON favorite_tags (tagId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS html_progress (versionedId TEXT NOT NULL PRIMARY KEY, anchor TEXT NOT NULL, offset REAL NOT NULL, ratio REAL NOT NULL)")
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        listOf(
            "CREATE TABLE IF NOT EXISTS authors (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, affiliations TEXT NOT NULL, paperCount INTEGER, citationCount INTEGER, hIndex INTEGER, updatedAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS paper_authors (paperId TEXT NOT NULL, authorId TEXT NOT NULL, PRIMARY KEY(paperId, authorId))",
            "CREATE TABLE IF NOT EXISTS followed_authors (authorId TEXT NOT NULL PRIMARY KEY, followedAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS keywords (text TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS recommendation_feedback (paperId TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS discovery_candidates (paperId TEXT NOT NULL, source TEXT NOT NULL, discoveredAt INTEGER NOT NULL, PRIMARY KEY(paperId, source))",
            "CREATE TABLE IF NOT EXISTS inbox (paperId TEXT NOT NULL PRIMARY KEY, reasons TEXT NOT NULL, receivedAt INTEGER NOT NULL, isRead INTEGER NOT NULL, archived INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS discovery_sync (id TEXT NOT NULL PRIMARY KEY, windowStart INTEGER NOT NULL, windowEnd INTEGER NOT NULL, nextStart INTEGER NOT NULL, initialized INTEGER NOT NULL, completedAt INTEGER NOT NULL, error TEXT)",
            "CREATE TABLE IF NOT EXISTS semantic_cache (`key` TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, fetchedAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS pending_author_papers (paperId TEXT NOT NULL, authorId TEXT NOT NULL, since INTEGER NOT NULL, silent INTEGER NOT NULL, PRIMARY KEY(paperId, authorId))"
        ).forEach(db::execSQL)
    }
}
