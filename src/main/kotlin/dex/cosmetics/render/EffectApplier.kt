package dex.cosmetics.render

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.types.EffectCosmetic
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d

/** Per-tick driver for [EffectCosmetic]s — dispatches by effect variant. */
object EffectApplier {

    fun tick(player: ServerPlayerEntity, cosmetic: EffectCosmetic) {
        when (val effect = cosmetic.effect) {
            is EffectCosmetic.Effect.Particle.Style -> tickStyleParticle(player, effect)
            is EffectCosmetic.Effect.Particle.Command -> tickCommandParticle(player, effect)
            is EffectCosmetic.Effect.Glow -> tickGlow(player)
            is EffectCosmetic.Effect.Snowstorm -> tickSnowstorm(player, effect)
        }
    }

    private fun tickStyleParticle(
        player: ServerPlayerEntity,
        effect: EffectCosmetic.Effect.Particle.Style,
    ) {
        val world = player.world as? ServerWorld ?: return
        if (world.time % effect.intervalTicks != 0L) return

        val baseX = player.x + effect.offset.x
        val baseY = player.y + effect.offset.y
        val baseZ = player.z + effect.offset.z
        val r = effect.radius.toDouble()

        val count = effect.count.coerceAtMost(DexCosmetics.config.particleTickBudgetPerPlayer)
        if (count <= 0) return
        when (effect.style) {
            EffectCosmetic.StyleType.POINT ->
                world.spawnParticles(
                    effect.particle,
                    baseX,
                    baseY,
                    baseZ,
                    count,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                )
            EffectCosmetic.StyleType.TRAIL ->
                world.spawnParticles(
                    effect.particle,
                    baseX,
                    baseY,
                    baseZ,
                    count,
                    0.05,
                    0.05,
                    0.05,
                    0.0,
                )
            EffectCosmetic.StyleType.HALO,
            EffectCosmetic.StyleType.SQUARE -> {
                for ((x, z) in
                    ParticleShapes.outline(
                        effect.style == EffectCosmetic.StyleType.SQUARE,
                        count,
                        r,
                    )) {
                    world.spawnParticles(
                        effect.particle,
                        baseX + x,
                        baseY,
                        baseZ + z,
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                    )
                }
            }
            EffectCosmetic.StyleType.ORBIT -> {
                val phase = (world.time % 80L) / 80.0 * Math.PI * 2
                world.spawnParticles(
                    effect.particle,
                    baseX + r * cos(phase),
                    baseY,
                    baseZ + r * sin(phase),
                    count,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                )
            }
        }
    }

    /**
     * Run each configured command at the player's location with op-level + silent feedback, so `~ ~
     * ~` and `^ ^ ^` work and chat doesn't get spammed with "Spawned X particles".
     */
    private fun tickCommandParticle(
        player: ServerPlayerEntity,
        effect: EffectCosmetic.Effect.Particle.Command,
    ) {
        val world = player.world as? ServerWorld ?: return
        if (world.time % effect.intervalTicks != 0L) return

        val source = player.commandSource.withMaxLevel(4).withSilent()
        val commandManager = world.server.commandManager
        for (cmd in effect.commands) {
            try {
                commandManager.executeWithPrefix(source, cmd)
            } catch (e: Exception) {
                DexCosmetics.LOGGER.warn(
                    "Particle command failed for cosmetic on player {}: '{}' — {}",
                    player.name.string,
                    cmd,
                    e.message,
                )
            }
        }
    }

    /** Refresh GLOWING once per second with a 60-tick duration so the outline never gaps. */
    private fun tickGlow(player: ServerPlayerEntity) {
        if (player.world.time % 20L != 0L) return
        player.addStatusEffect(
            StatusEffectInstance(StatusEffects.GLOWING, 60, 0, true, false, false)
        )
    }

    private fun tickSnowstorm(player: ServerPlayerEntity, effect: EffectCosmetic.Effect.Snowstorm) {
        val world = player.world as? ServerWorld ?: return
        if (world.time % effect.intervalTicks != 0L) return
        val pos =
            Vec3d(
                player.x + effect.offset.x,
                player.y + effect.offset.y,
                player.z + effect.offset.z,
            )
        SnowstormDispatcher.dispatch(world, effect.particleId, pos, effect.broadcastRadius)
    }
}
