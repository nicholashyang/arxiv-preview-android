package com.example.arxivpreview

import android.content.Context
import androidx.room.Room
import com.example.arxivpreview.data.FavoriteRepository
import com.example.arxivpreview.data.PaperRepository
import com.example.arxivpreview.data.PdfFileDownloader
import com.example.arxivpreview.data.PdfRepository
import com.example.arxivpreview.data.PreferencesRepository
import com.example.arxivpreview.data.update.AppUpdateRepository
import com.example.arxivpreview.data.local.ArxivDatabase
import com.example.arxivpreview.data.remote.ArxivParser
import com.example.arxivpreview.data.remote.ArxivRemoteDataSource
import com.example.arxivpreview.data.remote.ArxivService
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class AppContainer(context: Context) {
    val context: Context = context.applicationContext

    val database: ArxivDatabase = Room.databaseBuilder(
        this.context,
        ArxivDatabase::class.java,
        "arxiv-preview.db",
    ).addMigrations(com.example.arxivpreview.data.local.MIGRATION_1_2).build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "ArxivPreview/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
        )
        .build()

    private val arxivService = Retrofit.Builder()
        .baseUrl("https://export.arxiv.org/")
        .client(client)
        .build()
        .create(ArxivService::class.java)

    private val remote = ArxivRemoteDataSource(arxivService, ArxivParser())
    val preferencesRepository = PreferencesRepository(this.context)
    val appUpdateRepository = AppUpdateRepository(
        this.context,
        client.newBuilder().followSslRedirects(false).callTimeout(9, TimeUnit.MINUTES).build(),
        preferencesRepository,
    )
    val paperRepository = PaperRepository(database, database.dao(), remote)
    val favoriteRepository = FavoriteRepository(database.dao())
    val pdfFileDownloader = PdfFileDownloader(client)
    val pdfRepository = PdfRepository(this.context, database.dao(), pdfFileDownloader)
}
