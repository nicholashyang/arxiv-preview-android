package com.example.arxivpreview.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface ArxivService {
    @GET("api/query")
    suspend fun search(
        @Query("search_query") query: String,
        @Query("start") start: Int,
        @Query("max_results") maxResults: Int,
        @Query("sortBy") sortBy: String,
        @Query("sortOrder") sortOrder: String,
    ): ResponseBody
}
