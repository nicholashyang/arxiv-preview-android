package com.example.arxivpreview.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun listRoundTrip_preservesValues() {
        val values = listOf("Alice Example", "Bob Example", "cs.AI")
        assertEquals(values, converters.stringToList(converters.listToString(values)))
    }

    @Test
    fun emptyListRoundTrip_isEmpty() {
        assertEquals(emptyList<String>(), converters.stringToList(converters.listToString(emptyList())))
    }
}
