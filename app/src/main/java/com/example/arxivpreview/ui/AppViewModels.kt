package com.example.arxivpreview.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.arxivpreview.AppContainer
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.data.AppPreferences
import com.example.arxivpreview.data.update.AppUpdateState
import com.example.arxivpreview.data.update.UpdatePhase
import com.example.arxivpreview.data.local.DownloadEntity
import com.example.arxivpreview.model.Paper
import com.example.arxivpreview.worker.DailySyncScheduler
import com.example.arxivpreview.worker.AppUpdateScheduler
import com.example.arxivpreview.worker.PdfDownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val preferences = container.preferencesRepository.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val appUpdate = container.appUpdateRepository.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUpdateState(),
    )

    fun dismissUpdatePrompt(release: com.example.arxivpreview.data.update.AppRelease) {
        viewModelScope.launch { container.appUpdateRepository.dismissPrompt(release) }
    }

}

class OnboardingViewModel(
    private val container: AppContainer,
    application: Application,
) : AndroidViewModel(application) {
    val selected = MutableStateFlow(setOf<String>())

    fun toggle(code: String) {
        selected.update { current -> if (code in current) current - code else current + code }
    }

    fun finish() {
        if (selected.value.isEmpty()) return
        viewModelScope.launch {
            container.preferencesRepository.finishOnboarding(selected.value)
            DailySyncScheduler.schedule(getApplication())
        }
    }
}

data class LatestUiState(
    val papers: List<Paper> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class LatestViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(LatestUiState())
    val state: StateFlow<LatestUiState> = mutableState

    init {
        viewModelScope.launch {
            container.paperRepository.observeLatest().collect { papers ->
                mutableState.update { it.copy(papers = papers) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (mutableState.value.refreshing) return
        viewModelScope.launch {
            val categories = container.preferencesRepository.preferences.first().categories
            if (categories.isEmpty()) return@launch
            mutableState.update { it.copy(refreshing = true, error = null) }
            runCatching { container.paperRepository.refreshLatest(categories) }
                .onSuccess { page ->
                    mutableState.update {
                        it.copy(
                            refreshing = false,
                            hasMore = page.papers.size < page.totalResults,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            refreshing = false,
                            error = error.message ?: "Could not refresh papers",
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.loadingMore || current.refreshing || !current.hasMore) return
        viewModelScope.launch {
            val categories = container.preferencesRepository.preferences.first().categories
            mutableState.update { it.copy(loadingMore = true, error = null) }
            runCatching { container.paperRepository.refreshLatest(categories, append = true) }
                .onSuccess { page ->
                    val totalLoaded = mutableState.value.papers.size
                    mutableState.update {
                        it.copy(
                            loadingMore = false,
                            hasMore = page.papers.isNotEmpty() && totalLoaded < page.totalResults,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            loadingMore = false,
                            error = error.message ?: "Could not load more papers",
                        )
                    }
                }
        }
    }
}

data class SearchUiState(
    val draft: com.example.arxivpreview.model.SearchCriteria = com.example.arxivpreview.model.SearchCriteria(),
    val applied: com.example.arxivpreview.model.SearchCriteria? = null,
    val papers: List<Paper> = emptyList(), val total: Int = 0, val nextStart: Int = 0,
    val searching: Boolean = false, val loadingMore: Boolean = false, val hasMore: Boolean = false, val error: String? = null,
)
class SearchViewModel(private val execute: suspend (com.example.arxivpreview.model.SearchCriteria, Int) -> com.example.arxivpreview.model.PaperPage) : ViewModel() {
    constructor(container: AppContainer) : this({ criteria, start -> container.paperRepository.search(criteria, start) })
    val state = MutableStateFlow(SearchUiState())
    private var job: kotlinx.coroutines.Job? = null
    private var generation = 0
    fun edit(criteria: com.example.arxivpreview.model.SearchCriteria) { state.update { it.copy(draft = criteria) } }
    fun clear() { job?.cancel(); generation++; state.value = SearchUiState() }
    fun search() = submit(state.value.draft)
    fun retry() { if (state.value.papers.isNotEmpty()) loadMore() else submit(state.value.applied ?: state.value.draft) }
    private fun submit(criteria: com.example.arxivpreview.model.SearchCriteria) {
        criteria.validate()?.let { error -> state.update { it.copy(error = error) }; return }
        job?.cancel()
        val token = ++generation
        state.update { it.copy(applied = criteria, papers = emptyList(), searching = true, loadingMore = false, hasMore = false, error = null, total = 0, nextStart = 0) }
        job = viewModelScope.launch { fetch(criteria, 0, token) }
    }
    fun loadMore() {
        val current = state.value
        if (current.searching || current.loadingMore || !current.hasMore) return
        val criteria = current.applied ?: return
        val token = generation
        state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch { fetch(criteria, current.nextStart, token) }
    }
    private suspend fun fetch(criteria: com.example.arxivpreview.model.SearchCriteria, start: Int, token: Int) {
        try {
            val page = execute(criteria, start)
            if (token != generation) return
            state.update { current ->
                val next = start + page.papers.size
                current.copy(papers = (current.papers + page.papers).distinctBy(Paper::id), total = page.totalResults,
                    nextStart = next, searching = false, loadingMore = false,
                    hasMore = criteria.exactId.isEmpty() && page.papers.isNotEmpty() && next < page.totalResults)
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { if (token == generation) state.update { it.copy(searching = false, loadingMore = false, error = error.message ?: "Search failed") } }
    }
}

class FavoritesViewModel(container: AppContainer) : ViewModel() {
    val papers = container.favoriteRepository.observeFavorites().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}

data class DetailUiState(
    val paper: Paper? = null,
    val favorite: Boolean = false,
    val download: DownloadEntity? = null,
    val downloadRunning: Boolean = false,
    val downloadProgress: Int = 0,
    val error: String? = null,
)

class DetailViewModel(
    private val paperId: String,
    private val container: AppContainer,
) : ViewModel() {
    val state: StateFlow<DetailUiState> = combine(
        container.paperRepository.observePaper(paperId),
        container.favoriteRepository.observeIsFavorite(paperId),
        container.pdfRepository.observeDownload(paperId),
        WorkManager.getInstance(container.context)
            .getWorkInfosForUniqueWorkLiveData(PdfDownloadWorker.uniqueName(paperId))
            .asFlow(),
    ) { paper, favorite, download, workInfos ->
        val work = workInfos.firstOrNull()
        DetailUiState(
            paper = paper,
            favorite = favorite,
            download = download,
            downloadRunning = work?.state == WorkInfo.State.ENQUEUED ||
                work?.state == WorkInfo.State.RUNNING,
            downloadProgress = work?.progress?.getInt(PdfDownloadWorker.KEY_PROGRESS, 0) ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch {
            if (container.paperRepository.getPaper(paperId) == null) {
                runCatching { container.paperRepository.refreshPaper(paperId) }
            }
        }
    }

    fun toggleFavorite() {
        val current = state.value
        val paper = current.paper ?: return
        viewModelScope.launch {
            container.favoriteRepository.setFavorite(paper, !current.favorite)
        }
    }

    fun download() = container.pdfRepository.enqueueOfflineDownload(paperId)

    fun deleteDownload() {
        viewModelScope.launch { container.pdfRepository.deleteOfflineDownload(paperId) }
    }
}

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val saved: Boolean = false,
    val update: AppUpdateState = AppUpdateState(),
    val updateQueued: Boolean = false,
)

class SettingsViewModel(
    private val container: AppContainer,
    application: Application,
) : AndroidViewModel(application) {
    val state = combine(
        container.preferencesRepository.preferences,
        container.appUpdateRepository.state,
        WorkManager.getInstance(container.context)
            .getWorkInfosForUniqueWorkLiveData(AppUpdateScheduler.MANUAL_WORK).asFlow(),
    ) { preferences, update, work ->
        SettingsUiState(
            preferences = preferences,
            update = update,
            updateQueued = work.any { !it.state.isFinished } && update.phase == UpdatePhase.IDLE,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSwipe(left: Boolean, action: com.example.arxivpreview.data.SwipeAction) {
        viewModelScope.launch { container.preferencesRepository.setSwipe(left, action) }
    }
    fun checkForAppUpdate() = AppUpdateScheduler.checkNow(container.context)
    fun downloadAppUpdate() = AppUpdateScheduler.download(container.context)
    suspend fun appUpdateInstallIntent() = container.appUpdateRepository.installIntent()
    fun reportAppUpdateError(message: String) {
        viewModelScope.launch { container.appUpdateRepository.reportError(message) }
    }

    fun setThemeMode(mode: com.example.arxivpreview.data.ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun saveCategories(categories: Set<String>) {
        if (categories.isEmpty()) return
        viewModelScope.launch {
            container.preferencesRepository.setCategories(categories)
            container.paperRepository.refreshLatest(categories)
            DailySyncScheduler.schedule(getApplication())
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setNotificationsEnabled(enabled)
            if (enabled) DailySyncScheduler.schedule(getApplication())
        }
    }
}

class PdfViewModel(
    private val paperId: String,
    private val container: AppContainer,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val fileUri: android.net.Uri? = null,
        val pdfUrl: String? = null,
        val paper: Paper? = null,
        val error: String? = null,
    )

    val state = MutableStateFlow(State())

    private var loadJob: kotlinx.coroutines.Job? = null

    init { retry() }

    fun retry(forceDownload: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.value = State()
            val paper = container.paperRepository.getPaper(paperId)
            if (paper == null) {
                state.value = State(loading = false, error = "Paper not found")
                return@launch
            }
            state.value = state.value.copy(pdfUrl = paper.pdfUrl, paper = paper)
            try {
                val file = container.pdfRepository.ensurePreview(paperId, forceDownload)
                state.value = State(loading = false, fileUri = container.pdfRepository.contentUri(file), pdfUrl = paper.pdfUrl, paper = paper)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) {
                state.value = State(loading = false, pdfUrl = paper.pdfUrl, paper = paper, error = error.message ?: "Could not download PDF")
            }

        }
    }
}

object ViewModelFactories {
    fun main(container: AppContainer) = factory { MainViewModel(container) }
    fun onboarding(container: AppContainer, app: Application) =
        factory { OnboardingViewModel(container, app) }
    fun latest(container: AppContainer) = factory { LatestViewModel(container) }
    fun search(container: AppContainer) = factory { SearchViewModel(container) }
    fun favorites(container: AppContainer) = factory { FavoritesViewModel(container) }
    fun detail(id: String, container: AppContainer) = factory { DetailViewModel(id, container) }
    fun settings(container: AppContainer, app: Application) =
        factory { SettingsViewModel(container, app) }
    fun pdf(id: String, container: AppContainer) = factory { PdfViewModel(id, container) }

    private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) =
        object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                if (!modelClass.isAssignableFrom(T::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
                @Suppress("UNCHECKED_CAST")
                return create() as VM
            }
        }
}
