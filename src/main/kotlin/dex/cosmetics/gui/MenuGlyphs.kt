package dex.cosmetics.gui

/**
 * Deterministic Private-Use-Area codepoint allocation for the generated carousel-menu glyphs,
 * derived purely from a category's index in the [MenuRing]. Both [MenuStripGenerator] (which bakes
 * the PNGs and writes the matching `font/gui.json` providers) and [MenuPageGui] (which references
 * them in titles) use this, so the two never drift.
 *
 * Layout for a ring of N categories (index `i` = `0..N-1`), [FRAMES] frames per transition: rest
 * BASE + i cycleRight BASE + N + i*FRAMES + frame cycleLeft BASE + N + N*FRAMES + i*FRAMES + frame
 *
 * BASE is well clear of the hand-authored glyphs (menus U+E001.., spaces U+E100../E200..).
 */
class MenuGlyphs(private val ring: MenuRing) {

    private val n = ring.size
    private val restBase = BASE
    private val rightBase = BASE + n
    private val leftBase = BASE + n + n * FRAMES

    fun rest(index: Int): String = char(restBase + index)

    fun cycleRight(index: Int, frame: Int): String = char(rightBase + index * FRAMES + frame)

    fun cycleLeft(index: Int, frame: Int): String = char(leftBase + index * FRAMES + frame)

    fun cycleRightFrames(index: Int): List<String> = (0 until FRAMES).map { cycleRight(index, it) }

    fun cycleLeftFrames(index: Int): List<String> = (0 until FRAMES).map { cycleLeft(index, it) }

    companion object {
        const val BASE = 0xE300
        const val FRAMES = 8

        private fun char(cp: Int): String = String(Character.toChars(cp))
    }
}
