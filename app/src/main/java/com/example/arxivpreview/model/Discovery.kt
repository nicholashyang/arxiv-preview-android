package com.example.arxivpreview.model

import com.example.arxivpreview.data.local.AuthorEntity

typealias AuthorProfile = AuthorEntity
data class AuthorSummary(val id: String, val name: String)
data class AuthorWork(val id: String, val title: String, val arxivId: String?, val year: Int?, val date: String?, val url: String)
data class AuthorPage(val papers: List<AuthorWork>, val next: Int?, val fetchedAt: Long, val stale: Boolean)
data class RecommendationItem(val paper: Paper, val reasons: List<String>, val score: Double, val earlier: Boolean)
data class InboxItem(val paper: Paper, val reasons: List<String>, val receivedAt: Long, val isRead: Boolean, val archived: Boolean)
