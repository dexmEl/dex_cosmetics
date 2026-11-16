package dex.cosmetics.render

import dex.cosmetics.DexCosmetics
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

/**
 * Bridge to Cobblemon's `SpawnSnowstormParticlePacket`. Cobblemon is `modCompileOnly` — not
 * required at runtime; dispatch is a no-op when absent. The Cobblemon class reference is kept
 * inside [doDispatch] (not a field) so the JVM only resolves it when actually called.
 */
object SnowstormDispatcher {

    private val cobblemonLoaded: Boolean = FabricLoader.getInstance().isModLoaded("cobblemon")
    private var warnedMissing: Boolean = false

    init {
        if (cobblemonLoaded) {
            DexCosmetics.LOGGER.info(
                "Snowstorm dispatcher: cobblemon detected, snowstorm effect cosmetics enabled"
            )
        } else {
            DexCosmetics.LOGGER.info(
                "Snowstorm dispatcher: cobblemon not loaded, snowstorm effect cosmetics will be skipped"
            )
        }
    }

    /** Fire a burst. No-op without cobblemon; warns once per lifetime. */
    fun dispatch(world: ServerWorld, particleId: Identifier, pos: Vec3d, broadcastRadius: Double) {
        if (!cobblemonLoaded) {
            if (!warnedMissing) {
                DexCosmetics.LOGGER.warn(
                    "Snowstorm cosmetic equipped but cobblemon is not installed — particle '{}' will not render",
                    particleId,
                )
                warnedMissing = true
            }
            return
        }
        doDispatch(world, particleId, pos, broadcastRadius)
    }

    private fun doDispatch(
        world: ServerWorld,
        particleId: Identifier,
        pos: Vec3d,
        broadcastRadius: Double,
    ) {
        com.cobblemon.mod.common.net.messages.client.effect
            .SpawnSnowstormParticlePacket(particleId, pos)
            .let { packet ->
                packet.sendToPlayersAround(
                    pos.x,
                    pos.y,
                    pos.z,
                    broadcastRadius,
                    world.registryKey,
                ) { player ->
                    !ServerPlayNetworking.canSend(player, packet.id)
                }
            }
    }
}
