package com.example.arxivpreview.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class SearchStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(condition())
    }
    private fun paper(id: String) = Paper(id, "${id}v1", id, "", emptyList(), 0,0,"",emptyList(),"","",null,null)
    @Test fun lateResponsesCannotReplaceNewSearchAndPaginationUsesSubmittedCriteria() {
        val calls = CopyOnWriteArrayList<Pair<SearchCriteria, Int>>()
        val old = CompletableDeferred<PaperPage>()
        lateinit var vm: SearchViewModel
        main {
            vm = SearchViewModel { criteria, start ->
                calls += criteria to start
                when {
                    criteria.keywords == "old" -> withContext(NonCancellable) { old.await() }
                    start == 0 -> PaperPage(listOf(paper("2501.00001"), paper("2501.00001")), 4, 0, 2)
                    else -> PaperPage(listOf(paper("2501.00002")), 3, start, 1)
                }
            }
            vm.edit(SearchCriteria(keywords = "old")); vm.search()
        }
        waitFor { calls.size == 1 }
        main { vm.edit(SearchCriteria(keywords = "new")); vm.search() }
        waitFor { !vm.state.value.searching }
        old.complete(PaperPage(listOf(paper("2501.99999")), 1, 0, 1))
        main { vm.edit(SearchCriteria(keywords = "unsubmitted")); vm.loadMore() }
        waitFor { !vm.state.value.loadingMore && calls.size == 3 }
        assertEquals("new", calls.last().first.keywords)
        assertEquals(2, calls.last().second)
        assertEquals(listOf("2501.00001", "2501.00002"), vm.state.value.papers.map { it.id })
        assertFalse(vm.state.value.hasMore)
        main { vm.clear() }
    }
}
