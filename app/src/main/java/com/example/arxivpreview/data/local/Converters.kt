package com.example.arxivpreview.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun listToString(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
