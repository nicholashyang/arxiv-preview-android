package com.example.arxivpreview.ui

import androidx.lifecycle.*
import com.example.arxivpreview.AppContainer
import com.example.arxivpreview.data.discovery.*
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

internal fun <T : ViewModel> discoveryFactory(create: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

data class HomeCategoryUiState(val category: ArxivCategory, val unreadCount: Int)

class DiscoveryViewModel(val container: AppContainer) : ViewModel() {
    val homeCategories = combine(container.preferencesRepository.preferences, container.inboxRepository.unreadByCategory) { preferences, unread ->
        ArxivCategories.all.filter { it.code in preferences.categories }
            .map { HomeCategoryUiState(it, unread[it.code] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recommendations = container.recommendationRepository.recommendations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val inbox = container.inboxRepository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sync = container.discoverySync.state
    val sources = container.database.discoveryDao().observeSync().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val onlineWarning = container.semanticScholar.warning
    val authors = container.authorRepository.followed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val keywords = container.database.discoveryDao().observeKeywords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val feedback = container.database.discoveryDao().observeFeedback().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)
    val keyConfigured = MutableStateFlow(container.semanticKeyStore.read() != null)
    val keyStatus = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null
    fun refreshIfStale() = run {
        val sources = container.database.discoveryDao().observeSync().first()
        if (sources.isEmpty() || System.currentTimeMillis() - (sources.maxOfOrNull { it.completedAt } ?: 0L) > 3_600_000) refresh()
    }
    fun refresh() {
        if (refreshJob?.isActive == true || sync.value.running) return
        refreshJob = viewModelScope.launch { attempt { container.discoverySync.refresh() } }
    }
    fun run(block: suspend () -> Unit) { viewModelScope.launch { attempt(block) } }
    private suspend fun attempt(block: suspend () -> Unit) {
        try { error.value = null; block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.message ?: "Could not complete action" }
    }
    fun addKeyword(text: String) = run {
        val cleaned = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        require(cleaned.isNotBlank() && cleaned.length <= 120 && cleaned.none { it.isISOControl() }) { "Enter a topic of 1–120 characters" }
        if (container.database.discoveryDao().observeKeywords().first().none { it.text == cleaned })
            container.database.discoveryDao().keyword(KeywordEntity(cleaned, System.currentTimeMillis()))
    }
    fun removeKeyword(text: String) = run {
        container.database.discoveryDao().removeKeyword(text)
        container.database.discoveryDao().deleteSync("keyword:$text")
    }
    fun saveKey(value: String) = run {
        withContext(Dispatchers.IO) { container.semanticKeyStore.save(value) }
        container.database.discoveryDao().clearCache()
        keyConfigured.value = true
        keyStatus.value = "Saved securely. Use Validate to check access."
    }
    fun deleteKey() = run {
        withContext(Dispatchers.IO) { container.semanticKeyStore.delete() }
        keyConfigured.value = false; keyStatus.value = "Using public access."
        container.database.discoveryDao().clearCache()
    }
    fun validateKey() = run {
        keyStatus.value = "Checking access…"
        try {
            container.semanticScholar.get("graph/v1/author/1741101", mapOf("fields" to "name"), force = true, allowStale = false)
            keyStatus.value = "Access check succeeded. Individual endpoints may have different limits."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { keyStatus.value = e.message ?: "Access check failed" }
    }
}

data class AuthorUiState(val profile: AuthorProfile? = null, val works: List<AuthorWork> = emptyList(), val next: Int? = null,
    val loading: Boolean = false, val error: String? = null, val stale: Boolean = false)
class AuthorViewModel(private val id: String, val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(AuthorUiState())
    val following = container.authorRepository.followed.map { a -> a.any { it.id == id } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    init { refresh() }
    fun refresh() = load(false)
    fun more() = load(true)
    private fun load(append: Boolean) {
        if (state.value.loading || (append && state.value.next == null)) return
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = container.authorRepository.profile(id, force = !append && state.value.profile != null)
                state.update { it.copy(profile = profile) }
                val page = container.authorRepository.works(id, if (append) state.value.next ?: 0 else 0)
                state.update { it.copy(works = ((if (append) it.works else emptyList()) + page.papers).distinctBy(AuthorWork::id), next = page.next, loading = false, stale = page.stale) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(loading = false, error = e.message ?: "Could not load author") } }
        }
    }
    fun follow() { viewModelScope.launch {
        try { container.authorRepository.follow(id, !following.value) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.update { it.copy(error = e.message) } }
    } }
    fun open(work: AuthorWork, onPaper: (Paper) -> Unit) { viewModelScope.launch {
        try { container.authorRepository.open(work)?.let(onPaper) ?: error("This paper is not available on arXiv yet") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.update { it.copy(error = e.message) } }
    } }
}

data class AuthorSearchState(val query: String = "", val results: List<AuthorSummary> = emptyList(), val next: Int? = null, val loading: Boolean = false, val error: String? = null, val submitted: Boolean = false)
class AuthorSearchViewModel(private val repository: AuthorRepository) : ViewModel() {
    val state = MutableStateFlow(AuthorSearchState())
    private var job: Job? = null
    private var generation = 0
    fun search(query: String) {
        job?.cancel(); generation++
        if (query.isBlank()) { state.value = AuthorSearchState(); return }
        state.value = AuthorSearchState(query.trim(), loading = true, submitted = true)
        fetch(0, generation)
    }
    fun more() { val s = state.value; if (!s.loading && s.next != null) { state.update { it.copy(loading = true, error = null) }; fetch(s.next, generation) } }
    private fun fetch(offset: Int, token: Int) {
        job = viewModelScope.launch {
            try {
                val (authors, next) = repository.search(state.value.query, offset)
                if (token == generation) state.update { it.copy(results = (it.results + authors).distinctBy { a -> a.id }, next = next, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}
