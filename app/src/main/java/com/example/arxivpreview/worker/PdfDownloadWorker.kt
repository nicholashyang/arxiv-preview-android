package com.example.arxivpreview.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.data.PdfRepository
import com.example.arxivpreview.data.local.DownloadEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val paperId = inputData.getString(KEY_PAPER_ID) ?: return@withContext Result.failure()
        val container = (applicationContext as ArxivApplication).container
        val paper = container.database.dao().getPaper(paperId) ?: return@withContext Result.failure()
        val file = File(
            applicationContext.filesDir,
            "pdfs/${PdfRepository.safeFileName(paper.versionedId)}.pdf",
        )
        try {
            val bytes = container.pdfFileDownloader.download(paper.pdfUrl, file) { progress ->
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            }
            container.database.dao().upsertDownload(
                DownloadEntity(
                    paperId = paperId,
                    versionedId = paper.versionedId,
                    filePath = file.absolutePath,
                    bytes = bytes,
                    downloadedAt = System.currentTimeMillis(),
                ),
            )
            Result.success()
        } catch (error: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PAPER_ID = "paper_id"
        const val KEY_PROGRESS = "progress"
        fun uniqueName(paperId: String) = "pdf-download-$paperId"
    }
}
