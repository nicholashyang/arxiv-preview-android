package com.example.arxivpreview.ui

/** Coordinates are physical pixels relative to the layout, not the display. */
internal data class PaneBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)
}

internal data class PaneFold(val bounds: PaneBounds, val vertical: Boolean)
internal data class PaneLayout(val primary: PaneBounds, val secondary: PaneBounds? = null)

internal fun calculatePaneLayout(
    width: Int,
    height: Int,
    density: Float,
    fold: PaneFold? = null,
    allowTwoPanes: Boolean = false,
): PaneLayout {
    val full = PaneBounds(0, 0, width, height)
    val bounds = fold?.bounds
    if (fold != null && bounds != null) {
        val start = if (fold.vertical) bounds.left else bounds.top
        val end = if (fold.vertical) bounds.right else bounds.bottom
        val extent = if (fold.vertical) width else height
        val crossesPane = if (fold.vertical) bounds.top <= 0 && bounds.bottom >= height
            else bounds.left <= 0 && bounds.right >= width
        if (crossesPane && start < extent && end > 0) {
            // Leave breathing room even for a zero-width crease in book/tabletop posture.
            val clearance = (12 * density).toInt()
            val before = (start - clearance).coerceIn(0, extent)
            val after = (end + clearance).coerceIn(0, extent)
            val first = if (fold.vertical) PaneBounds(0, 0, before, height)
                else PaneBounds(0, 0, width, before)
            val second = if (fold.vertical) PaneBounds(after, 0, width, height)
                else PaneBounds(0, after, width, height)
            val fits = if (fold.vertical) first.width >= 320 * density && second.width >= 320 * density && height >= 480 * density
                else width >= 600 * density && first.height >= 240 * density && second.height >= 240 * density
            if (allowTwoPanes && fits) return PaneLayout(first, second)
            return PaneLayout(if (first.width.toLong() * first.height >= second.width.toLong() * second.height) first else second)
        }
    }
    // 760dp of content plus the 80dp navigation rail is an expanded 840dp window.
    if (allowTwoPanes && width >= 760 * density && height >= 480 * density) {
        val listWidth = (width * .42f).toInt().coerceIn((320 * density).toInt(), (440 * density).toInt())
        val gap = density.toInt().coerceAtLeast(1)
        return PaneLayout(PaneBounds(0, 0, listWidth, height), PaneBounds(listWidth + gap, 0, width, height))
    }
    return PaneLayout(full)
}
