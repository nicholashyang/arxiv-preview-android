package com.example.arxivpreview.model

import org.junit.Assert.*
import org.junit.Test

class ArxivIdsTest {
    @Test fun preservesLegacyCategoryAndVersion() {
        assertEquals("hep-th/9901001v3", ArxivIds.versioned("http://arxiv.org/abs/hep-th/9901001v3"))
        assertEquals("hep-th/9901001", ArxivIds.normalize("https://arxiv.org/pdf/hep-th/9901001v3.pdf"))
    }
    @Test fun handlesModernIdsAndUrlSuffixes() {
        assertEquals("2303.08774v6", ArxivIds.versioned("https://arxiv.org/html/2303.08774v6#S1"))
        assertEquals("2303.08774", ArxivIds.normalize("2303.08774v6"))
        assertEquals("math.GT/0309136", ArxivIds.normalize("math.GT/0309136v1"))
    }
    @Test fun rejectsPathsThatAreNotIdentifiers() {
        listOf("", "../private", "9901001", "https://example.org/other/path", "2303.08774<script>").forEach {
            assertEquals("", ArxivIds.versioned(it))
        }
    }
}
