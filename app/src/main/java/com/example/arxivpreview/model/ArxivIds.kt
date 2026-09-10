package com.example.arxivpreview.model

object ArxivIds {
    private val identifier = Regex("(?:[0-9]{4}\\.[0-9]{4,5}|[A-Za-z][A-Za-z0-9.-]*/[0-9]{7})(?:v[0-9]+)?")
    fun versioned(value: String): String {
        val path = value.trim().substringBefore('?').substringBefore('#').removeSuffix(".pdf")
            .replace(Regex("^https?://[^/]+/(?:abs|pdf|html)/"), "")
        return path.takeIf { identifier.matches(it) }.orEmpty()
    }
    fun normalize(value: String): String = versioned(value).replace(Regex("v[0-9]+$"), "")
}

val Paper.canonicalVersionedId: String
    get() = ArxivIds.versioned(versionedId).ifBlank {
        ArxivIds.versioned(abstractUrl).ifBlank { ArxivIds.versioned(pdfUrl) }
    }
val Paper.htmlUrl: String get() = "https://arxiv.org/html/$canonicalVersionedId"
