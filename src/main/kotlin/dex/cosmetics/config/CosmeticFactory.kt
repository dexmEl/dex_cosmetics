package dex.cosmetics.config

import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.cosmetic.types.BackpackCosmetic
import dex.cosmetics.cosmetic.types.EffectCosmetic
import dex.cosmetics.cosmetic.types.HatCosmetic
import dex.cosmetics.cosmetic.types.ItemSkinCosmetic
import net.minecraft.particle.DustParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.particle.SimpleParticleType
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f

/** Converts validated definitions after Minecraft's registries and item tags are available. */
internal object CosmeticFactory {
    fun build(def: CosmeticDefinition): Cosmetic {
        DefinitionValidator.validate(def)
        val model = Identifier.of(def.model)
        val texture = Identifier.of(def.texture)
        val parent = Identifier.of(def.modelParent)
        val scale = Vector3f(def.transform.scaleX, def.transform.scaleY, def.transform.scaleZ)
        return when (CosmeticType.fromConfigKey(def.type)) {
            CosmeticType.HAT ->
                HatCosmetic(
                    def.id,
                    def.displayName,
                    def.description,
                    model,
                    texture,
                    parent,
                    Vector3f(def.transform.offsetX, def.transform.offsetY, def.transform.offsetZ),
                    scale,
                )
            CosmeticType.BACKPACK ->
                BackpackCosmetic(
                    def.id,
                    def.displayName,
                    def.description,
                    model,
                    texture,
                    parent,
                    scale,
                )
            CosmeticType.ITEM_SKIN -> {
                val (ids, tags) = ItemSkinCosmetic.parseCompatible(def.itemSkin.compatibleItems)
                ids.forEach {
                    require(Registries.ITEM.containsId(it)) { "Unknown compatible item: $it" }
                }
                tags.forEach { tag ->
                    require(Registries.ITEM.any { Registries.ITEM.getEntry(it).isIn(tag) }) {
                        "Unknown or empty item tag: ${tag.id}"
                    }
                }
                ItemSkinCosmetic(
                    def.id,
                    def.displayName,
                    def.description,
                    model,
                    texture,
                    parent,
                    ids,
                    tags,
                )
            }
            CosmeticType.EFFECT ->
                EffectCosmetic(
                    def.id,
                    def.displayName,
                    def.description,
                    model,
                    texture,
                    parent,
                    effect(def),
                )
            else -> error("Unsupported cosmetic type: ${def.type}")
        }
    }

    private fun effect(def: CosmeticDefinition): EffectCosmetic.Effect =
        when (def.effect.kind.lowercase()) {
            "glow" ->
                EffectColors.parseGlowColor(def.effect.glow.color).let { (rgb, fmt) ->
                    EffectCosmetic.Effect.Glow(rgb, fmt)
                }
            "snowstorm" ->
                with(def.effect.snowstorm) {
                    EffectCosmetic.Effect.Snowstorm(
                        Identifier.of(particleId),
                        intervalTicks,
                        broadcastRadius,
                        Vec3d(offsetX, offsetY, offsetZ),
                    )
                }
            "particle" ->
                with(def.effect.particle) {
                    if (style.isBlank()) {
                        EffectCosmetic.Effect.Particle.Command(
                            intervalTicks,
                            (listOf(command) + commands)
                                .map(String::trim)
                                .filter(String::isNotEmpty),
                        )
                    } else {
                        val id = Identifier.of(particleType)
                        require(Registries.PARTICLE_TYPE.containsId(id)) { "Unknown particle: $id" }
                        val type = Registries.PARTICLE_TYPE.get(id)
                        val particle =
                            if (type === ParticleTypes.DUST)
                                DustParticleEffect(EffectColors.parseDustColor(color), 1f)
                            else
                                type as? SimpleParticleType
                                    ?: error("Particle $id requires parameters; use command mode")
                        EffectCosmetic.Effect.Particle.Style(
                            intervalTicks,
                            EffectCosmetic.StyleType.valueOf(style.uppercase()),
                            particle,
                            count,
                            radius,
                            Vector3f(offsetX, offsetY, offsetZ),
                        )
                    }
                }
            else -> error("Unsupported effect kind")
        }
}
