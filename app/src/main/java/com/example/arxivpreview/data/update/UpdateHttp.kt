package com.example.arxivpreview.data.update

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Close the socket on WorkManager cancellation, including while blocked reading the APK. */
internal suspend fun <T> OkHttpClient.withUpdateResponse(
    request: Request,
    block: suspend (Response) -> T,
): T = withContext(Dispatchers.IO) {
    coroutineScope {
        val call = newCall(request)
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { block(it) }
        } finally {
            cancellation.cancel()
        }
    }
}
