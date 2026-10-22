package com.example.arxivpreview.ui

import org.junit.Assert.*
import org.junit.Test

class PaneLayoutPolicyTest {
    @Test fun compactAndShortWindowsStaySinglePane() {
        assertNull(calculatePaneLayout(411, 850, 1f, allowTwoPanes = true).secondary)
        assertNull(calculatePaneLayout(1000, 400, 1f, allowTwoPanes = true).secondary)
        assertNull(calculatePaneLayout(759, 700, 1f, allowTwoPanes = true).secondary)
    }

    @Test fun expandedContentUsesReadableListAndDetailWidthsAtAnyDensity() {
        val layout = calculatePaneLayout(760, 700, 1f, allowTwoPanes = true)
        assertEquals(320, layout.primary.width)
        assertEquals(439, layout.secondary!!.width)
        assertNotNull(calculatePaneLayout(1520, 1400, 2f, allowTwoPanes = true).secondary)
        assertNull(calculatePaneLayout(1519, 1400, 2f, allowTwoPanes = true).secondary)
        assertEquals(440, calculatePaneLayout(2000, 1000, 1f, allowTwoPanes = true).primary.width)
    }

    @Test fun bookPostureUsesBothSidesAndAvoidsTheHinge() {
        val fold = PaneFold(PaneBounds(390, 0, 410, 900), vertical = true)
        val layout = calculatePaneLayout(800, 900, 1f, fold, true)
        assertEquals(PaneBounds(0, 0, 378, 900), layout.primary)
        assertEquals(PaneBounds(422, 0, 800, 900), layout.secondary)
    }

    @Test fun tabletopUsesTopAndBottomWhenBothAreUsable() {
        val fold = PaneFold(PaneBounds(0, 400, 800, 420), vertical = false)
        val layout = calculatePaneLayout(800, 850, 1f, fold, true)
        assertEquals(PaneBounds(0, 0, 800, 388), layout.primary)
        assertEquals(PaneBounds(0, 432, 800, 850), layout.secondary)
    }

    @Test fun narrowAndSinglePaneScreensChooseTheLargerSafeRegion() {
        val book = PaneFold(PaneBounds(180, 0, 200, 700), vertical = true)
        assertEquals(PaneLayout(PaneBounds(212, 0, 800, 700)), calculatePaneLayout(800, 700, 1f, book, true))
        val tabletop = PaneFold(PaneBounds(0, 300, 400, 320), vertical = false)
        assertEquals(PaneLayout(PaneBounds(0, 332, 400, 850)), calculatePaneLayout(400, 850, 1f, tabletop, true))
        assertEquals(PaneLayout(PaneBounds(0, 432, 800, 850)), calculatePaneLayout(800, 850, 1f, tabletop.copy(bounds = PaneBounds(0, 400, 800, 420))))
    }

    @Test fun zeroWidthCreasesStillHaveClearanceAndOutsideFoldsAreIgnored() {
        val crease = PaneFold(PaneBounds(400, 0, 400, 800), vertical = true)
        val layout = calculatePaneLayout(800, 800, 1f, crease, true)
        assertEquals(388, layout.primary.right)
        assertEquals(412, layout.secondary!!.left)
        assertEquals(PaneLayout(PaneBounds(0, 0, 500, 800)), calculatePaneLayout(500, 800, 1f, crease.copy(bounds = PaneBounds(-100, 0, -80, 800)), true))
    }
}
