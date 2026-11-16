package dex.cosmetics.gui

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.CosmeticType

/**
 * The ordered, cyclic list of cosmetic categories shown in the carousel menu, resolved from
 * [dex.cosmetics.config.CosmeticsConfig.menuCategories]. Entries are in left-to-right ring order;
 * neighbours wrap around. The same resolution is used by both the strip generator (which bakes
 * per-focus transition art) and [MenuPageGui] (which navigates), so codepoints and navigation
 * always agree.
 *
 * A clean fill-loop needs ≥4 categories (3 visible + 1 cycling in). With fewer, the menu still
 * works but [twoLeft]/[twoRight] alias a visible card; callers should expect that degenerate case.
 */
class MenuRing
private constructor(
    val categories: List<CosmeticType>,
    /** Index in [categories] that the menu opens focused on. */
    val defaultFocus: Int,
) {
    val size: Int
        get() = categories.size

    operator fun get(index: Int): CosmeticType = categories[wrap(index)]

    fun indexOf(type: CosmeticType): Int = categories.indexOf(type)

    private fun wrap(i: Int): Int = ((i % size) + size) % size

    fun left(i: Int): CosmeticType = get(i - 1)

    fun right(i: Int): CosmeticType = get(i + 1)

    fun twoLeft(i: Int): CosmeticType = get(i - 2)

    fun twoRight(i: Int): CosmeticType = get(i + 2)

    /** New focus index after a cycle-right (row shifts right; left neighbour becomes focus). */
    fun afterCycleRight(i: Int): Int = wrap(i - 1)

    /** New focus index after a cycle-left (row shifts left; right neighbour becomes focus). */
    fun afterCycleLeft(i: Int): Int = wrap(i + 1)

    companion object {
        /**
         * Resolve the ring from the live config: the enabled (`true`) entries of `menu_categories`,
         * in map order, dropping unknown keys. If nothing is enabled, falls back to all types.
         */
        fun fromConfig(): MenuRing {
            val cfg = DexCosmetics.config
            val resolved =
                cfg.menuCategories
                    .filterValues { it }
                    .keys
                    .mapNotNull { key ->
                        CosmeticType.fromConfigKey(key).also {
                            if (it == null)
                                DexCosmetics.LOGGER.warn(
                                    "Unknown menu_categories key '{}' — skipping",
                                    key,
                                )
                        }
                    }
            val categories =
                resolved.ifEmpty {
                    DexCosmetics.LOGGER.warn(
                        "No menu categories enabled — falling back to all types"
                    )
                    CosmeticType.entries.toList()
                }
            val focusType = CosmeticType.fromConfigKey(cfg.menuFocus)
            val focus = categories.indexOf(focusType).let { if (it >= 0) it else 0 }
            return MenuRing(categories, focus)
        }
    }
}
