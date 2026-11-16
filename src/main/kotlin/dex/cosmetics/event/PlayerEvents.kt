package dex.cosmetics.event

import dex.cosmetics.DexCosmetics
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

object PlayerEvents {
    fun register() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player ?: return@register
            DexCosmetics.service.reconcile(player.uuid)
            DexCosmetics.ticker.onPlayerJoin(player)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player ?: return@register
            DexCosmetics.ticker.onPlayerQuit(player)
            DexCosmetics.playerData.onQuit(player.uuid)
        }
        // Respawn/dim change replaces the ServerPlayerEntity → re-sync cosmetics against the new
        // one.
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            DexCosmetics.ticker.onPlayerRespawnOrChangeWorld(newPlayer)
        }
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { player, _, _ ->
            DexCosmetics.ticker.onPlayerRespawnOrChangeWorld(player)
        }
    }
}
