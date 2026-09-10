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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ArxivDatabase : RoomDatabase() {
    abstract fun dao(): ArxivDao
}
