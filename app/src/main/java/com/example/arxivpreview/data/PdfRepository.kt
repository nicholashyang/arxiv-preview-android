package com.example.arxivpreview.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.arxivpreview.data.local.ArxivDao
import com.example.arxivpreview.data.local.DownloadEntity
import com.example.arxivpreview.worker.PdfDownloadWorker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PdfRepository(
    private val context: Context,
    private val dao: ArxivDao,
    private val downloader: PdfFileDownloader,
) {
    fun observeDownload(paperId: String): Flow<DownloadEntity?> = dao.observeDownload(paperId)

    suspend fun ensurePreview(paperId: String, forceDownload: Boolean = false): File = withContext(Dispatchers.IO) {
        val paper = requireNotNull(dao.getPaper(paperId)) { "Paper not found" }
        dao.getDownload(paperId)?.let { stored ->
            val file = File(stored.filePath)
            if (forceDownload) {
                val bytes = downloader.download(paper.pdfUrl, file)
                dao.upsertDownload(stored.copy(versionedId = paper.versionedId, bytes = bytes, downloadedAt = System.currentTimeMillis()))
                return@withContext file
            }
            if (file.exists() && runCatching { downloader.validatePdf(file) }.isSuccess) return@withContext file
        }
        val target = File(context.cacheDir, "pdfs/${safeFileName(paper.versionedId)}.pdf")
        if (forceDownload || !target.exists() || runCatching { downloader.validatePdf(target) }.isFailure) {
            downloader.download(paper.pdfUrl, target)
        }
        target
    }

    fun enqueueOfflineDownload(paperId: String) {
        val request = OneTimeWorkRequestBuilder<PdfDownloadWorker>()
            .setInputData(Data.Builder().putString(PdfDownloadWorker.KEY_PAPER_ID, paperId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PdfDownloadWorker.uniqueName(paperId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun deleteOfflineDownload(paperId: String) {
        val record = dao.getDownload(paperId)
        if (record != null) File(record.filePath).delete()
        dao.removeDownload(paperId)
    }

    fun contentUri(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    companion object {
        fun safeFileName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
