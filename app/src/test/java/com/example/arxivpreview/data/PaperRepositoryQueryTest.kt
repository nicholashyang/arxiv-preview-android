package com.example.arxivpreview.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperRepositoryQueryTest {
    @Test
    fun categoryQuery_isStableAndSorted() {
        assertEquals(
            "(cat:cs.AI OR cat:cs.LG)",
            PaperRepository.categoryQuery(setOf("cs.LG", "cs.AI")),
        )
    }

    @Test
    fun searchQuery_escapesQuotesAndAddsCategory() {
        assertEquals(
            """all:"large \"language\" model" AND cat:cs.CL""",
            PaperRepository.searchQuery("""large "language" model""", "cs.CL"),
        )
    }
}
