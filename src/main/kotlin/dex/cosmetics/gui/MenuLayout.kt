package dex.cosmetics.gui

/**
 * Clickable slot regions for the carousel [MenuPageGui] (9×6 = 54 slots, 0-indexed; slot = row*9 +
 * col). The whole menu visual is drawn in the title image, so these are invisible click zones
 * overlaid on it:
 * - [CENTER_SLOTS] → the focused (centre) card: opens that category's browser
 * - [LEFT_ARROW_SLOTS] → the left `<` arrow: bring the LEFT neighbour to focus (cards slide right)
 * - [RIGHT_ARROW_SLOTS] → the right `>` arrow: bring the RIGHT neighbour to focus (cards slide
 *   left)
 *
 * Positions are approximate against the template art (left arrow ≈ cols 1-2, right arrow ≈ cols
 * 7-8, centre card ≈ cols 3-5) — nudge if the hit-boxes feel off in-game.
 */
internal object MenuLayout {
    val CENTER_SLOTS: List<Int> = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32, 39, 40, 41)

    val LEFT_ARROW_SLOTS: List<Int> = listOf(18, 19, 27, 28)
    val RIGHT_ARROW_SLOTS: List<Int> = listOf(25, 26, 34, 35)
}
