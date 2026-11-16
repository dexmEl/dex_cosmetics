package dex.cosmetics.gui

import com.google.gson.JsonParser
import dex.cosmetics.DexCosmetics
import kotlin.math.abs
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

/**
 * GUI background images via the title-glyph trick: a custom-font bitmap glyph in the GUI title
 * renders as an image at the title's draw position. Vertical placement comes from each provider's
 * `ascent` field in `font/gui.json`; horizontal from a custom `x_offset` we read at startup and
 * emit as a string of negative/positive space-provider chars.
 */
object GuiBackground {

    /** Identifier of our custom font (referenced by the providers in font/gui.json). */
    val FONT: Identifier = DexCosmetics.id("gui")

    /**
     * Font for the generated carousel-menu glyphs (font/menu.json, written by
     * [dex.cosmetics.polymer.MenuStripGenerator]). Kept separate from [FONT] so the generated file
     * never collides with the hand-authored gui.json. Its space provider mirrors gui.json's, so
     * [spaceFor] works in this font too.
     */
    val MENU_FONT: Identifier = DexCosmetics.id("menu")

    /** Horizontal shift applied to every full-screen menu glyph, baked into [menuImage]. */
    private const val MENU_X_OFFSET = -8

    object Glyph {
        /** Browser-screen backgrounds. */
        val HATS: String = Char(0xE002).toString()
        val BACK: String = Char(0xE003).toString()
        val EFFECTS: String = Char(0xE004).toString()
        val ITEMS: String = Char(0xE005).toString()

        /**
         * Browser-screen pagination variants. Each browser (hats/items/effects/back) has 4 PNGs: a
         * base "no arrows" version + 3 paginated variants (right-arrow on first page, both on
         * middle pages, left-arrow on last). [CosmeticBrowserGui.titleGlyph] picks one based on the
         * current page state.
         */
        val HATS_RIGHT_ARROW: String = Char(0xE009).toString()
        val HATS_BOTH_ARROWS: String = Char(0xE00A).toString()
        val HATS_LEFT_ARROW: String = Char(0xE00B).toString()
        val ITEMS_RIGHT_ARROW: String = Char(0xE00C).toString()
        val ITEMS_BOTH_ARROWS: String = Char(0xE00D).toString()
        val ITEMS_LEFT_ARROW: String = Char(0xE00E).toString()
        val EFFECTS_RIGHT_ARROW: String = Char(0xE00F).toString()
        val EFFECTS_BOTH_ARROWS: String = Char(0xE010).toString()
        val EFFECTS_LEFT_ARROW: String = Char(0xE011).toString()
        val BACK_RIGHT_ARROW: String = Char(0xE012).toString()
        val BACK_BOTH_ARROWS: String = Char(0xE013).toString()
        val BACK_LEFT_ARROW: String = Char(0xE014).toString()

        /**
         * Placeholder-category browser backgrounds (balloons / pokemon) — registered in gui.json.
         */
        val BALLOONS: String = Char(0xE015).toString()
        val BALLOONS_RIGHT_ARROW: String = Char(0xE016).toString()
        val BALLOONS_BOTH_ARROWS: String = Char(0xE017).toString()
        val BALLOONS_LEFT_ARROW: String = Char(0xE018).toString()
        val POKEMON: String = Char(0xE019).toString()
        val POKEMON_RIGHT_ARROW: String = Char(0xE01A).toString()
        val POKEMON_BOTH_ARROWS: String = Char(0xE01B).toString()
        val POKEMON_LEFT_ARROW: String = Char(0xE01C).toString()
    }

    /** Per-glyph horizontal pixel shift, read once from gui.json's `x_offset` field. */
    private val xOffsets: Map<String, Int> = loadXOffsets()

    /**
     * Title that draws ONLY the bitmap image — no visible text after. Vertical position comes from
     * `ascent`; horizontal from the JSON's `x_offset`.
     */
    fun imageOnly(glyph: String): MutableText {
        val style = Style.EMPTY.withFont(FONT).withColor(Formatting.WHITE)
        val shift = xOffsets[glyph] ?: 0
        return Text.literal(spaceFor(shift) + glyph).setStyle(style)
    }

    /**
     * Title that draws a generated carousel-menu glyph (rest image or transition frame) in the
     * [MENU_FONT], shifted by the fixed [MENU_X_OFFSET] so the 256px composite aligns with the GUI.
     */
    fun menuImage(glyph: String): MutableText {
        val style = Style.EMPTY.withFont(MENU_FONT).withColor(Formatting.WHITE)
        return Text.literal(spaceFor(MENU_X_OFFSET) + glyph).setStyle(style)
    }

    /** Title that draws the image, rewinds by `rewindPixels`, then appends a small text label. */
    fun imageWithLabel(glyph: String, label: Text, rewindPixels: Int): MutableText {
        val style = Style.EMPTY.withFont(FONT).withColor(Formatting.WHITE)
        val shift = xOffsets[glyph] ?: 0
        val composed = Text.literal(spaceFor(shift) + glyph).setStyle(style)
        val rewind = spaceFor(-rewindPixels)
        if (rewind.isNotEmpty()) composed.append(Text.literal(rewind).setStyle(style))
        composed.append(label)
        return composed
    }

    /**
     * Returns a string of space-provider chars whose advances sum to [px]. Negative values use
     * U+E100..E108; positive values use U+E200..E208. Supports magnitudes up to 511px.
     */
    private fun spaceFor(px: Int): String {
        if (px == 0) return ""
        val sign = if (px < 0) -1 else 1
        var remaining = abs(px)
        val sb = StringBuilder()
        for (size in intArrayOf(256, 128, 64, 32, 16, 8, 4, 2, 1)) {
            while (remaining >= size) {
                val ch = SPACE_CHARS[sign * size] ?: break
                sb.append(ch)
                remaining -= size
            }
        }
        return sb.toString()
    }

    /** Powers-of-two space chars. Negative magnitudes at 0xE100+n, positive at 0xE200+n. */
    private val SPACE_CHARS: Map<Int, String> = buildMap {
        for (n in 0..8) put(-(1 shl n), Char(0xE100 + n).toString())
        for (n in 0..8) put(1 shl n, Char(0xE200 + n).toString())
    }

    /** Read gui.json from the classpath and extract each bitmap provider's `x_offset`. */
    private fun loadXOffsets(): Map<String, Int> {
        val path = "assets/dex_cosmetics/font/gui.json"
        val stream =
            javaClass.classLoader.getResourceAsStream(path)
                ?: return emptyMap<String, Int>().also {
                    DexCosmetics.LOGGER.warn(
                        "Font config $path not found on classpath; GUI bitmaps will not be horizontally shifted"
                    )
                }
        val text = stream.bufferedReader().use { it.readText() }
        val result = mutableMapOf<String, Int>()
        try {
            val providers = JsonParser.parseString(text).asJsonObject.getAsJsonArray("providers")
            for (element in providers) {
                val obj = element.asJsonObject
                if (obj.get("type")?.asString != "bitmap") continue
                val xOffset = obj.get("x_offset")?.asInt ?: 0
                val chars = obj.getAsJsonArray("chars") ?: continue
                for (charElement in chars) result[charElement.asString] = xOffset
            }
        } catch (e: Exception) {
            DexCosmetics.LOGGER.warn("Failed to parse $path for x_offset values", e)
        }
        return result
    }
}
