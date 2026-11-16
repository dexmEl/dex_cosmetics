package dex.cosmetics.config

import com.google.gson.annotations.SerializedName

data class CosmeticDefinition(
    val id: String,
    val type: String,
    @SerializedName("display_name") val displayName: String,
    val description: List<String> = emptyList(),
    val model: String,
    val texture: String,
    @SerializedName("model_parent") val modelParent: String = "minecraft:item/generated",
    val transform: TransformBlock = TransformBlock(),
    @SerializedName("item_skin") val itemSkin: ItemSkinBlock = ItemSkinBlock(),
    @SerializedName("effect") val effect: EffectBlock = EffectBlock(),
) {
    data class TransformBlock(
        @SerializedName("offset_x") val offsetX: Float = 0.0f,
        @SerializedName("offset_y") val offsetY: Float = 0.0f,
        @SerializedName("offset_z") val offsetZ: Float = 0.0f,
        @SerializedName("scale_x") val scaleX: Float = 1.0f,
        @SerializedName("scale_y") val scaleY: Float = 1.0f,
        @SerializedName("scale_z") val scaleZ: Float = 1.0f,
    )

    data class ItemSkinBlock(
        @SerializedName("compatible_items") val compatibleItems: List<String> = emptyList()
    )

    /**
     * [kind] selects which sub-block applies:
     * - `"particle"` → [particle] (vanilla particles, style or command mode)
     * - `"glow"` → [glow] (colored outline + tinted GUI icon)
     * - `"snowstorm"` → [snowstorm] (Bedrock particle via Cobblemon; cobblemon required)
     */
    data class EffectBlock(
        val kind: String = "particle",
        val particle: ParticleBlock = ParticleBlock(),
        val glow: GlowBlock = GlowBlock(),
        val snowstorm: SnowstormBlock = SnowstormBlock(),
    )

    /**
     * Snowstorm cadence — actual particle behavior lives in the referenced `.particle.json`.
     * [particleId] matches the `description.identifier` field in that file. [offsetX/Y/Z] are
     * world-space (not rotated by player yaw).
     */
    data class SnowstormBlock(
        @SerializedName("particle_id") val particleId: String = "",
        @SerializedName("interval_ticks") val intervalTicks: Int = 20,
        @SerializedName("broadcast_radius") val broadcastRadius: Double = 32.0,
        @SerializedName("offset_x") val offsetX: Double = 0.0,
        @SerializedName("offset_y") val offsetY: Double = 1.0,
        @SerializedName("offset_z") val offsetZ: Double = 0.0,
    )

    /**
     * Glow tuning. [color] accepts a hex string (`#FF5555` / `0xFF5555`) or a vanilla Formatting
     * name (`red`, `dark_red`, `gold`, etc.). Unknown values fall back to white.
     */
    data class GlowBlock(val color: String = "white")

    /**
     * Vanilla-particle config — two authoring modes:
     * - **Style**: [style] is one of `point` / `trail` / `halo` / `orbit` / `square`, parameterized
     *   by [particleType], [count], [radius], offset, and [color] (for `dust`).
     * - **Command**: leave [style] empty and set [command] or [commands] — raw vanilla commands run
     *   at the wearer's position with op level + silent feedback. Style wins if non-empty.
     */
    data class ParticleBlock(
        @SerializedName("interval_ticks") val intervalTicks: Int = 4,

        // ---- Style mode ----
        val style: String = "",
        @SerializedName("particle_type") val particleType: String = "minecraft:end_rod",
        val color: String = "#FF0000",
        val count: Int = 8,
        val radius: Float = 0.6f,
        @SerializedName("offset_x") val offsetX: Float = 0.0f,
        @SerializedName("offset_y") val offsetY: Float = 0.5f,
        @SerializedName("offset_z") val offsetZ: Float = 0.0f,

        // ---- Command mode ----
        val command: String = "",
        val commands: List<String> = emptyList(),
    )
}
