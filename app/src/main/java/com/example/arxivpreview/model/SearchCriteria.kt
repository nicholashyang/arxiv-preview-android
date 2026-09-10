package com.example.arxivpreview.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class MatchMode(val label: String) { ALL("All words"), ANY("Any word"), PHRASE("Exact phrase") }
enum class SearchSort(val label: String, val api: String) {
    RELEVANCE("Relevance", "relevance"), SUBMITTED("Submission date", "submittedDate"), UPDATED("Update date", "lastUpdatedDate")
}
data class SearchCriteria(
    val keywords: String = "", val title: String = "", val author: String = "", val abstractText: String = "",
    val categories: Set<String> = emptySet(), val from: String = "", val until: String = "",
    val match: MatchMode = MatchMode.ALL, val sort: SearchSort = SearchSort.RELEVANCE, val ascending: Boolean = false,
) {
    val exactId: String get() = ArxivIds.versioned(keywords)
    fun query(): String {
        fun quote(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        fun field(prefix: String, text: String): String? {
            if (text.isBlank()) return null
            val words = if (match == MatchMode.PHRASE) listOf(text.trim()) else text.trim().split(Regex("\\s+"))
            return words.joinToString(if (match == MatchMode.ANY) " OR " else " AND ", "(", ")") { "$prefix:${quote(it)}" }
        }
        val parts = listOfNotNull(field("all", keywords), field("ti", title), field("au", author), field("abs", abstractText)).toMutableList()
        if (categories.isNotEmpty()) parts += categories.sorted().joinToString(" OR ", "(", ")") { "cat:$it" }
        if (from.isNotBlank() || until.isNotBlank()) {
            val first = if (from.isBlank()) LocalDate.of(1991, 1, 1) else LocalDate.parse(from)
            val last = if (until.isBlank()) LocalDate.of(9999, 12, 31) else LocalDate.parse(until)
            require(!first.isAfter(last)) { "Start date must not be after end date" }
            val format = DateTimeFormatter.BASIC_ISO_DATE
            parts += "submittedDate:[${first.format(format)}0000 TO ${last.format(format)}2359]"
        }
        require(parts.isNotEmpty()) { "Enter a search term, category, or date range" }
        return parts.joinToString(" AND ")
    }
    fun validate(): String? = if (exactId.isNotEmpty()) null else runCatching { query() }.exceptionOrNull()?.let {
        if (it is java.time.format.DateTimeParseException) "Use dates in YYYY-MM-DD format" else it.message
    }
    fun description(): String = if (exactId.isNotEmpty()) "Exact ID: $exactId" else listOfNotNull(
        keywords.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() }?.let { "Title: $it" },
        author.takeIf { it.isNotBlank() }?.let { "Author: $it" }, abstractText.takeIf { it.isNotBlank() }?.let { "Abstract: $it" },
        categories.sorted().joinToString(", ").takeIf { it.isNotEmpty() },
        "$from – $until".takeIf { from.isNotBlank() || until.isNotBlank() }, match.label,
        sort.label + if (sort != SearchSort.RELEVANCE) (if (ascending) " ↑" else " ↓") else "",
    ).joinToString(" · ")
}

fun Paper.shareText(): String = "$title\nhttps://arxiv.org/abs/$canonicalVersionedId"
