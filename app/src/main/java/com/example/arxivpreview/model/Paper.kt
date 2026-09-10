package com.example.arxivpreview.model

data class Paper(
    val id: String,
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

data class PaperPage(
    val papers: List<Paper>,
    val totalResults: Int,
    val startIndex: Int,
    val itemsPerPage: Int,
)
