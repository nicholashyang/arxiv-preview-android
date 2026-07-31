package com.example.arxivpreview.data.remote

import android.util.Xml
import com.example.arxivpreview.model.Paper
import com.example.arxivpreview.model.PaperPage
import java.io.InputStream
import java.time.Instant
import org.xmlpull.v1.XmlPullParser

class ArxivParser {
    fun parse(input: InputStream): PaperPage {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
        val papers = mutableListOf<Paper>()
        var totalResults = 0
        var startIndex = 0
        var itemsPerPage = 0
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "entry" -> papers += parseEntry(parser)
                "totalResults" -> totalResults = parser.nextText().trim().toIntOrNull() ?: 0
                "startIndex" -> startIndex = parser.nextText().trim().toIntOrNull() ?: 0
                "itemsPerPage" -> itemsPerPage = parser.nextText().trim().toIntOrNull() ?: 0
            }
        }
        return PaperPage(papers, totalResults, startIndex, itemsPerPage)
    }

    private fun parseEntry(parser: XmlPullParser): Paper {
        var rawId = ""
        var title = ""
        var summary = ""
        val authors = mutableListOf<String>()
        var published = 0L
        var updated = 0L
        var primaryCategory = ""
        val categories = mutableListOf<String>()
        var abstractUrl = ""
        var pdfUrl = ""
        var doi: String? = null
        var journalReference: String? = null
        val entryDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG &&
                parser.depth == entryDepth &&
                parser.name == "entry"
            ) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "id" -> rawId = parser.nextText().trim()
                "title" -> title = normalizeWhitespace(parser.nextText())
                "summary" -> summary = normalizeWhitespace(parser.nextText())
                "published" -> published = parseInstant(parser.nextText())
                "updated" -> updated = parseInstant(parser.nextText())
                "name" -> if (parser.depth > entryDepth) authors += normalizeWhitespace(parser.nextText())
                "primary_category" -> primaryCategory =
                    parser.getAttributeValue(null, "term").orEmpty()
                "category" -> parser.getAttributeValue(null, "term")?.let(categories::add)
                "doi" -> doi = parser.nextText().trim().ifBlank { null }
                "journal_ref" -> journalReference = parser.nextText().trim().ifBlank { null }
                "link" -> {
                    val href = parser.getAttributeValue(null, "href").orEmpty()
                    val rel = parser.getAttributeValue(null, "rel").orEmpty()
                    val type = parser.getAttributeValue(null, "type").orEmpty()
                    val linkTitle = parser.getAttributeValue(null, "title").orEmpty()
                    if (rel == "alternate") abstractUrl = href
                    if (linkTitle == "pdf" || type == "application/pdf") pdfUrl = href
                }
            }
        }
        val versionedId = rawId.substringAfterLast("/").ifBlank { rawId }
        val id = normalizeId(versionedId)
        return Paper(
            id = id,
            versionedId = versionedId,
            title = title,
            summary = summary,
            authors = authors.distinct(),
            publishedAt = published,
            updatedAt = updated,
            primaryCategory = primaryCategory.ifBlank { categories.firstOrNull().orEmpty() },
            categories = categories.distinct(),
            abstractUrl = abstractUrl.ifBlank { "https://arxiv.org/abs/$versionedId" },
            pdfUrl = pdfUrl.replace("http://", "https://")
                .ifBlank { "https://arxiv.org/pdf/$versionedId" },
            doi = doi,
            journalReference = journalReference,
        )
    }

    companion object {
        private val VERSION_SUFFIX = Regex("v\\d+$")
        private val WHITESPACE = Regex("\\s+")

        fun normalizeId(value: String): String =
            value.substringAfterLast("/").replace(VERSION_SUFFIX, "")

        fun normalizeWhitespace(value: String): String = value.trim().replace(WHITESPACE, " ")

        private fun parseInstant(value: String): Long =
            runCatching { Instant.parse(value.trim()).toEpochMilli() }.getOrDefault(0L)
    }
}
