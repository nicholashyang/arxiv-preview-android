package com.example.arxivpreview.ui

import com.example.arxivpreview.data.ThemeMode
import org.junit.Assert.*
import org.junit.Test

class ReaderPolicyTest {
    @Test fun newerAndroidStillRequiresPdfExtension() {
        assertFalse(supportsPdfViewer(35, 12))
        assertFalse(supportsPdfViewer(32, 0))
        assertFalse(supportsPdfViewer(30, 13))
        assertTrue(supportsPdfViewer(31, 13))
        assertTrue(supportsPdfViewer(35, 13))
    }
    @Test fun themeDefaultsAreCompatibleWithOldPreferences() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("future-value"))
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
    }
}
