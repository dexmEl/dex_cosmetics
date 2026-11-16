@file:Suppress("DEPRECATION")

package dex.cosmetics.util

import eu.pb4.placeholders.api.parsers.TextParserV1
import net.minecraft.text.MutableText
import net.minecraft.text.Text

/**
 * Helpers for parsing user- and dev-facing strings as MiniMessage-style markup.
 *
 * Both cosmetic configs (display_name / description) and our own GUI labels run through [mm], so
 * authors can write things like `<gold>Name</gold>`, `<rainbow>...`, `<gradient:#a:#b>...`,
 * `<bold>`, `<italic>`, `<#ff0000>raw hex</#ff0000>` etc.
 *
 * Plain strings without any tags are passed through unchanged (becomes a literal Text component).
 *
 * Backed by `eu.pb4:placeholder-api`'s [TextParserV1] which accepts MiniMessage-compatible syntax
 * and emits native Minecraft [Text] — no Adventure bridge needed.
 */
object Texts {

    /** Parse a MiniMessage-formatted string into a Minecraft [Text]. Empty string → empty Text. */
    fun mm(string: String): MutableText {
        if (string.isEmpty()) return Text.empty()
        return TextParserV1.DEFAULT.parseNode(string).toText().copy()
    }

    /** Parse each line in a list. */
    fun mm(lines: List<String>): List<MutableText> = lines.map { mm(it) }
}
