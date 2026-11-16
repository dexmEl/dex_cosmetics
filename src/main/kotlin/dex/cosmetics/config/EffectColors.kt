package dex.cosmetics.config

import net.minecraft.particle.DustParticleEffect
import net.minecraft.util.Formatting
import org.joml.Vector3f

internal object EffectColors {
    /** Parse `#RRGGBB` / `0xRRGGBB` into a Vector3f of channel floats; defaults to red. */
    fun parseDustColor(raw: String?): Vector3f {
        if (raw == null) return DustParticleEffect.RED
        val hex = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        val rgb = hex.toIntOrNull(16) ?: return DustParticleEffect.RED
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        return Vector3f(r, g, b)
    }

    /**
     * Parse `#FF5555`/`0xFF5555` (hex) or `red`/`dark_red` (Formatting names) into the tint
     * + outline Formatting pair. Unknown values fall back to white.
     */
    fun parseGlowColor(raw: String): Pair<Int, Formatting> {
        val trimmed = raw.trim()
        val hex =
            when {
                trimmed.startsWith("#") -> trimmed.substring(1)
                trimmed.startsWith("0x", ignoreCase = true) -> trimmed.substring(2)
                else -> null
            }
        if (hex != null) {
            val rgb = hex.toIntOrNull(16) ?: 0xFFFFFF
            return rgb to closestFormatting(rgb)
        }
        val fmt = Formatting.byName(trimmed.lowercase()) ?: Formatting.WHITE
        val rgb = fmt.colorValue ?: 0xFFFFFF
        return rgb to fmt
    }

    /**
     * Pick the vanilla Formatting color whose RGB is nearest the given hex (Euclidean distance).
     */
    private fun closestFormatting(rgb: Int): Formatting {
        val tr = (rgb shr 16) and 0xFF
        val tg = (rgb shr 8) and 0xFF
        val tb = rgb and 0xFF
        var best: Formatting = Formatting.WHITE
        var bestDist = Int.MAX_VALUE
        for (fmt in Formatting.values()) {
            val v = fmt.colorValue ?: continue
            val r = (v shr 16) and 0xFF
            val g = (v shr 8) and 0xFF
            val b = v and 0xFF
            val d = (r - tr) * (r - tr) + (g - tg) * (g - tg) + (b - tb) * (b - tb)
            if (d < bestDist) {
                bestDist = d
                best = fmt
            }
        }
        return best
    }
}
