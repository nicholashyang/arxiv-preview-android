package com.example.arxivpreview.data.discovery

import com.example.arxivpreview.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class SemanticHttpException(val status: Int, val retryAfter: Long? = null) : IOException(
    when (status) { 401, 403 -> "Semantic Scholar access restricted. Check your API key in Profile."
        429 -> "Semantic Scholar is busy. Try later or configure your API key in Profile."
        404 -> "Not indexed by Semantic Scholar yet."
        else -> "Semantic Scholar request failed ($status)." })

data class SemanticResponse(val payload: JSONObject, val fetchedAt: Long, val stale: Boolean = false)

/** Separate transport makes authentication, HTTP failures and retry behavior testable. */
fun interface SemanticTransport {
    suspend fun request(path: String, parameters: Map<String, String>, body: String?, key: String?): String
}
class OkHttpSemanticTransport(private val client: OkHttpClient) : SemanticTransport {
    override suspend fun request(path: String, parameters: Map<String, String>, body: String?, key: String?): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(buildUrl(path, parameters))
        if (!key.isNullOrBlank()) builder.header("x-api-key", key)
        if (body != null) builder.post(body.toRequestBody("application/json".toMediaType()))
        val call = client.newCall(builder.build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = runCatching {
                            if (!it.isSuccessful) throw SemanticHttpException(it.code, it.header("Retry-After")?.toLongOrNull())
                            it.body.string()
                        }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                }
            })
        }
    }
    companion object {
        internal fun buildUrl(path: String, parameters: Map<String, String>): HttpUrl {
            val builder = HttpUrl.Builder().scheme("https").host("api.semanticscholar.org")
            if (path.startsWith("graph/v1/paper/ARXIV:")) {
                builder.addPathSegments("graph/v1/paper").addPathSegment(path.removePrefix("graph/v1/paper/"))
            } else builder.addPathSegments(path.removePrefix("/"))
            parameters.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            return builder.build()
        }
    }

}

class SemanticScholarClient(private val dao: DiscoveryDao, private val transport: SemanticTransport,
    private val apiKey: () -> String?, private val interval: Long = 1_050,
    private val now: () -> Long = System::currentTimeMillis) {
    private val gate = Mutex()
    private var lastRequest = 0L
    val warning = MutableStateFlow<String?>(null)
    suspend fun get(path: String, parameters: Map<String, String> = emptyMap(), body: String? = null,
        ttl: Long = DAY, force: Boolean = false, allowStale: Boolean = true): SemanticResponse = gate.withLock {
        val raw = path + parameters.toSortedMap().toString() + body.orEmpty()
        val cacheKey = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        val cached = dao.cache(cacheKey)
        if (!force && cached != null && now() - cached.fetchedAt < ttl) return@withLock SemanticResponse(JSONObject(cached.payload), cached.fetchedAt)
        try {
            var attempt = 0
            while (true) {
                val elapsed = android.os.SystemClock.elapsedRealtime()
                delay((interval - (elapsed - lastRequest)).coerceAtLeast(0))
                lastRequest = android.os.SystemClock.elapsedRealtime()
                try {
                    val payload = transport.request(path, parameters, body, apiKey())
                    val json = JSONObject(payload)
                    val time = now()
                    dao.cache(SemanticCacheEntity(cacheKey, payload, time))
                    warning.value = null
                    return@withLock SemanticResponse(json, time)
                } catch (e: CancellationException) { throw e }
                catch (e: IOException) {
                    val retryable = e !is SemanticHttpException || e.status == 429 || e.status >= 500
                    if (!retryable || attempt++ >= 2) throw e
                    val seconds = (e as? SemanticHttpException)?.retryAfter
                    // Long server cooldowns are surfaced rather than keeping a UI request alive.
                    if (seconds != null && seconds > 30) throw e
                    delay(seconds?.times(1000) ?: (1000L shl attempt))
                }
            }
            @Suppress("UNREACHABLE_CODE") error("Unreachable")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            warning.value = e.message ?: "Online data is unavailable. Showing cached results."
            if (allowStale && cached != null) SemanticResponse(JSONObject(cached.payload), cached.fetchedAt, true) else throw e
        }
    }
    companion object { const val DAY = 86_400_000L; const val RECOMMENDATION_TTL = 6 * 3_600_000L }
}
