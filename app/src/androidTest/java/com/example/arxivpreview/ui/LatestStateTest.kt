package com.example.arxivpreview.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.model.Paper
import com.example.arxivpreview.model.PaperPage
import com.example.arxivpreview.model.SearchCriteria
import com.example.arxivpreview.model.SearchSort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class LatestStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @After fun cleanup() = main { store.clear() }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(condition())
    }
    private fun paper(id: String) = Paper(id, "${id}v1", id, "", emptyList(), 0, 0, "cs.AI", listOf("cs.AI"), "", "", null, null)

    @Test fun categoryAndSortStayFixedAndPaginationCountsDuplicates() {
        val calls = CopyOnWriteArrayList<Pair<SearchCriteria, Int>>()
        lateinit var vm: LatestViewModel
        main {
            vm = LatestViewModel("cs.AI", { criteria, start ->
                calls += criteria to start
                val papers = when (start) {
                    0 -> listOf(paper("one"), paper("two"))
                    2 -> listOf(paper("two"), paper("three"))
                    else -> emptyList()
                }
                PaperPage(papers, 5, start, 2)
            })
            store.put("latest", vm)
        }
        waitFor { !vm.state.value.refreshing }
        main { vm.loadMore() }
        waitFor { calls.size == 2 && !vm.state.value.loadingMore }
        assertEquals(listOf("one", "two", "three"), vm.state.value.papers.map { it.id })
        main { vm.loadMore() }
        waitFor { calls.size == 3 && !vm.state.value.loadingMore }
        assertEquals(listOf(0, 2, 4), calls.map { it.second })
        assertTrue(calls.all { it.first == SearchCriteria(categories = setOf("cs.AI"), sort = SearchSort.SUBMITTED) })
        assertFalse(vm.state.value.hasMore)
    }

    @Test fun failurePreservesCategoryCacheAndRetryRestartsAtFirstPage() {
        var fail = true
        val calls = CopyOnWriteArrayList<Int>()
        lateinit var vm: LatestViewModel
        main {
            vm = LatestViewModel("cs.AI", { _, start ->
                calls += start
                if (fail) throw IOException("Offline")
                PaperPage(listOf(paper("fresh")), 1, start, 1)
            }, { category ->
                assertEquals("cs.AI", category)
                listOf(paper("cached"))
            })
            store.put("latest", vm)
        }
        waitFor { !vm.state.value.refreshing }
        assertEquals("Offline", vm.state.value.error)
        assertEquals(listOf("cached"), vm.state.value.papers.map { it.id })
        assertFalse(vm.state.value.hasMore)
        main { fail = false; vm.retry() }
        waitFor { calls.size == 2 && !vm.state.value.refreshing }
        assertEquals(listOf(0, 0), calls)
        assertEquals(listOf("fresh"), vm.state.value.papers.map { it.id })
        assertNull(vm.state.value.error)
    }

    @Test fun refreshDiscardsLatePaginationAndOtherCategoryStateIsIndependent() {
        val oldPage = CompletableDeferred<PaperPage>()
        val oldFinished = CompletableDeferred<Unit>()
        var firstPages = 0
        lateinit var vm: LatestViewModel
        lateinit var other: LatestViewModel
        main {
            vm = LatestViewModel("cs.AI", { _, start ->
                if (start == 0) {
                    firstPages++
                    PaperPage(listOf(paper("fresh-$firstPages")), 2, 0, 1)
                } else withContext(NonCancellable) {
                    oldPage.await().also { oldFinished.complete(Unit) }
                }
            })
            other = LatestViewModel("hep-ph", { criteria, start ->
                assertEquals(setOf("hep-ph"), criteria.categories)
                PaperPage(listOf(paper("physics")), 1, start, 1)
            })
            store.put("latest", vm); store.put("other", other)
        }
        waitFor { !vm.state.value.refreshing && !other.state.value.refreshing }
        main { vm.loadMore() }
        waitFor { vm.state.value.loadingMore }
        main { vm.refresh() }
        waitFor { !vm.state.value.refreshing }
        oldPage.complete(PaperPage(listOf(paper("stale")), 2, 1, 1))
        waitFor { oldFinished.isCompleted }
        main {
            assertEquals(listOf("fresh-2"), vm.state.value.papers.map { it.id })
            assertEquals(listOf("physics"), other.state.value.papers.map { it.id })
            assertFalse(vm.state.value.loadingMore)
        }
    }
}
