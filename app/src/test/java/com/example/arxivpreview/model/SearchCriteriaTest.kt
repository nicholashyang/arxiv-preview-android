package com.example.arxivpreview.model

import org.junit.Assert.*
import org.junit.Test

class SearchCriteriaTest {
    @Test fun combinesFieldsCategoriesAndDates() {
        val query = SearchCriteria(keywords = "graph learning", title = "networks", author = "Smith",
            abstractText = "robust", categories = setOf("cs.LG", "cs.AI"), from = "2024-02-29", until = "2024-03-01").query()
        assertEquals("(all:\"graph\" AND all:\"learning\") AND (ti:\"networks\") AND (au:\"Smith\") AND (abs:\"robust\") AND (cat:cs.AI OR cat:cs.LG) AND submittedDate:[202402290000 TO 202403012359]", query)
    }
    @Test fun supportsAnyWordsPhrasesAndEscaping() {
        assertEquals("(ti:\"a\" OR ti:\"b\")", SearchCriteria(title = "a b", match = MatchMode.ANY).query())
        assertEquals("(au:\"Jane Doe\")", SearchCriteria(author = "Jane Doe", match = MatchMode.PHRASE).query())
        assertEquals("(all:\"a\\\"b\\\\c\")", SearchCriteria(keywords = "a\"b\\c").query())
    }
    @Test fun validatesEmptyAndDateBoundaries() {
        assertNotNull(SearchCriteria().validate())
        assertNotNull(SearchCriteria(from = "2023-02-29").validate())
        assertNotNull(SearchCriteria(from = "2024-02-02", until = "2024-02-01").validate())
        assertNull(SearchCriteria(categories = setOf("cs.AI")).validate())
        assertTrue(SearchCriteria(until = "2024-01-01").query().contains("199101010000"))
        assertTrue(SearchCriteria(from = "2024-01-01").query().contains("999912312359"))
    }
    @Test fun exactLookupPreservesVersionsAndIgnoresOtherFields() {
        val criteria = SearchCriteria(keywords = "https://arxiv.org/pdf/hep-th/9901001v2.pdf", from = "bad date")
        assertEquals("hep-th/9901001v2", criteria.exactId)
        assertNull(criteria.validate())
        assertEquals("Exact ID: hep-th/9901001v2", criteria.description())
        assertEquals("2501.12948v1", SearchCriteria(keywords = "2501.12948v1").exactId)
    }
    @Test fun sharingUsesVersionedCanonicalHttpsLink() {
        val paper = Paper("hep-th/9901001", "hep-th/9901001v2", "Title", "", emptyList(), 0, 0, "", emptyList(), "", "", null, null)
        assertEquals("Title\nhttps://arxiv.org/abs/hep-th/9901001v2", paper.shareText())
    }
}
