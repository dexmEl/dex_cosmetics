package dex.cosmetics.config

import dex.cosmetics.cosmetic.CosmeticType
import net.minecraft.util.Identifier

/** Validation before definitions enter the live registry. No world or server access. */
internal object DefinitionValidator {
    fun validate(def: CosmeticDefinition) {
        require(def.id.matches(Regex("[a-z0-9_-]+"))) {
            "id must contain only lowercase letters, digits, _ or -"
        }
        val type =
            requireNotNull(CosmeticType.fromConfigKey(def.type)) {
                "Unknown cosmetic type: ${def.type}"
            }
        require(type !in setOf(CosmeticType.BALLOON, CosmeticType.POKEMON)) {
            "${def.type} is a menu placeholder, not an implemented cosmetic type"
        }
        require(def.displayName.isNotBlank()) { "display_name must not be blank" }
        identifier(def.model, "model")
        identifier(def.texture, "texture")
        identifier(def.modelParent, "model_parent")
        with(def.transform) {
            listOf(offsetX, offsetY, offsetZ).forEach {
                bounded(it.toDouble(), -64.0, 64.0, "transform offset")
            }
            listOf(scaleX, scaleY, scaleZ).forEach {
                bounded(it.toDouble(), 0.01, 16.0, "transform scale")
            }
        }
        if (type == CosmeticType.ITEM_SKIN) {
            require(def.itemSkin.compatibleItems.isNotEmpty()) {
                "item_skin.compatible_items must not be empty"
            }
            def.itemSkin.compatibleItems.forEach {
                identifier(it.removePrefix("#"), "compatible_items entry")
            }
        }
        if (type != CosmeticType.EFFECT) return
        when (def.effect.kind.lowercase()) {
            "glow" -> {
                val color = def.effect.glow.color
                require(
                    color.matches(Regex("(?i)(#|0x)[0-9a-f]{6}")) || color.lowercase() in colors
                ) {
                    "Invalid glow color: $color"
                }
            }
            "snowstorm" ->
                with(def.effect.snowstorm) {
                    identifier(particleId, "snowstorm.particle_id")
                    require(intervalTicks in 1..72000) {
                        "snowstorm.interval_ticks must be in 1..72000"
                    }
                    bounded(broadcastRadius, 1.0, 256.0, "snowstorm.broadcast_radius")
                    listOf(offsetX, offsetY, offsetZ).forEach {
                        bounded(it, -64.0, 64.0, "snowstorm offset")
                    }
                }
            "particle" ->
                with(def.effect.particle) {
                    require(intervalTicks in 1..72000) {
                        "particle.interval_ticks must be in 1..72000"
                    }
                    if (style.isNotBlank()) {
                        require(
                            style.lowercase() in setOf("point", "trail", "halo", "orbit", "square")
                        ) {
                            "Unknown particle style: $style"
                        }
                        identifier(particleType, "particle.particle_type")
                        require(count in 1..4096) { "particle.count must be in 1..4096" }
                        bounded(radius.toDouble(), 0.0, 64.0, "particle.radius")
                        listOf(offsetX, offsetY, offsetZ).forEach {
                            bounded(it.toDouble(), -64.0, 64.0, "particle offset")
                        }
                        require(color.matches(Regex("(?i)(#|0x)[0-9a-f]{6}"))) {
                            "particle.color must be a six-digit hex color"
                        }
                    } else {
                        val all = listOf(command).plus(commands).filter(String::isNotBlank)
                        require(all.isNotEmpty() && all.size <= 16) {
                            "Provide 1..16 particle commands or a particle style"
                        }
                        require(all.all { it.length <= 4096 }) {
                            "Particle commands must be at most 4096 characters"
                        }
                    }
                }
            else -> error("Unknown effect kind: ${def.effect.kind}")
        }
    }

    private fun identifier(raw: String, field: String) {
        require(raw.isNotBlank() && Identifier.tryParse(raw) != null && !raw.contains("..")) {
            "$field is not a valid identifier: $raw"
        }
    }

    private fun bounded(value: Double, min: Double, max: Double, field: String) {
        require(value.isFinite() && value in min..max) { "$field must be finite and in $min..$max" }
    }

    private val colors =
        setOf(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white",
        )
}
