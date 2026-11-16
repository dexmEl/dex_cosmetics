package dex.cosmetics.cosmetic.types

import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import net.minecraft.particle.ParticleEffect
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f

/**
 * Cosmetic kind that applies a per-tick visual effect. Variants: vanilla particles
 * ([Effect.Particle]), GLOWING outline ([Effect.Glow]), Bedrock-format particles via Cobblemon
 * ([Effect.Snowstorm]). Adding a new effect = new [Effect] subclass + branch in
 * [dex.cosmetics.render.EffectApplier.tick] + new `kind` value in the config.
 */
data class EffectCosmetic(
    override val id: String,
    override val displayName: String,
    override val description: List<String>,
    override val modelId: Identifier,
    override val textureId: Identifier,
    override val modelParent: Identifier,
    val effect: Effect,
) : Cosmetic() {
    override val type: CosmeticType = CosmeticType.EFFECT

    sealed class Effect {
        /** Vanilla particles: a curated shape ([Style]) or raw commands ([Command]). */
        sealed class Particle : Effect() {
            abstract val intervalTicks: Int

            data class Style(
                override val intervalTicks: Int,
                val style: StyleType,
                val particle: ParticleEffect,
                val count: Int,
                val radius: Float,
                val offset: Vector3f,
            ) : Particle()

            data class Command(override val intervalTicks: Int, val commands: List<String>) :
                Particle()
        }

        /**
         * GLOWING outline. [colorRgb] tints the grayscale `glow_icon` for the GUI;
         * [outlineFormatting] picks the scoreboard team that colors the outline in-world.
         */
        data class Glow(val colorRgb: Int, val outlineFormatting: Formatting) : Effect()

        /**
         * Bedrock-format particle burst via Cobblemon. The `.particle.json` defines the visual; we
         * just fire spawn packets every [intervalTicks] ticks within [broadcastRadius]. Skipped
         * silently if Cobblemon isn't installed.
         */
        data class Snowstorm(
            val particleId: Identifier,
            val intervalTicks: Int,
            val broadcastRadius: Double,
            val offset: Vec3d,
        ) : Effect()
    }

    enum class StyleType {
        /** Single point at the offset position. */
        POINT,
        /** [count] particles at the player with velocity scatter. */
        TRAIL,
        /** Horizontal ring of [count] particles at [radius]. */
        HALO,
        /** Single point orbiting at [radius]; full revolution every 80 ticks. */
        ORBIT,
        /** Outline of a horizontal square of side `2 * radius`. */
        SQUARE,
    }
}
