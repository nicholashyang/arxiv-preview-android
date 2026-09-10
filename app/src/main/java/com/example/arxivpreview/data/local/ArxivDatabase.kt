package com.example.arxivpreview.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PaperEntity::class,
        FeedItemEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ArxivDatabase : RoomDatabase() {
    abstract fun dao(): ArxivDao
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
