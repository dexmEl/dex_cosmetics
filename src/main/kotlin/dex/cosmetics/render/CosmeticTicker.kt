package dex.cosmetics.render

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.cosmetic.types.BackpackCosmetic
import dex.cosmetics.cosmetic.types.EffectCosmetic
import dex.cosmetics.cosmetic.types.HatCosmetic
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.network.ServerPlayerEntity

class CosmeticTicker(private val registry: CosmeticRegistry) {
    /**
     * Players whose initial sync still needs to fire. Sync is deferred by one tick so the vanilla
     * equipment / tracker state for the freshly-spawned ServerPlayerEntity is in place before we
     * start pushing cosmetic packets at it.
     */
    private val pendingInitialSync = ConcurrentHashMap.newKeySet<UUID>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (pendingInitialSync.isNotEmpty()) {
                val toSync = pendingInitialSync.toList()
                pendingInitialSync.clear()
                for (uuid in toSync) {
                    val player = server.playerManager.getPlayer(uuid) ?: continue
                    syncFromEquipped(player)
                }
            }
            for (player in server.playerManager.playerList) {
                tickPlayer(player)
            }
            HatEquipmentBroadcaster.tick(server)
        }
    }

    fun onPlayerJoin(player: ServerPlayerEntity) {
        HatEquipmentBroadcaster.onPlayerJoin(player)
        pendingInitialSync.add(player.uuid)
    }

    fun onPlayerQuit(player: ServerPlayerEntity) {
        pendingInitialSync.remove(player.uuid)
        HatEquipmentBroadcaster.onPlayerQuit(player)
        GlowTeamManager.clear(player)
        BackPassengerRenderer.clear(player)
    }

    /**
     * Called after respawn / dimension change — same UUID, fresh ServerPlayerEntity. Vanilla drops
     * the back armor stand as an orphan and the new tracker state needs hat packets re-emitted
     * against the new entity ID, so we queue a sync just like onPlayerJoin.
     */
    fun onPlayerRespawnOrChangeWorld(newPlayer: ServerPlayerEntity) {
        BackPassengerRenderer.clear(newPlayer)
        pendingInitialSync.add(newPlayer.uuid)
    }

    /** Equip change pushed in from CosmeticService — rebuild renderable state for the player. */
    fun syncFromEquipped(player: ServerPlayerEntity) {
        val data = DexCosmetics.playerData.get(player.uuid) ?: return
        val equipped = data.equipped.snapshot()
        for (type in CosmeticType.entries) {
            val wantedId = equipped[type]?.takeIf(data::owns)
            when (type) {
                CosmeticType.HAT ->
                    HatEquipmentBroadcaster.setHat(
                        player,
                        wantedId?.let { registry.get(it) } as? HatCosmetic,
                    )
                CosmeticType.EFFECT -> {
                    val cosmetic = wantedId?.let { registry.get(it) } as? EffectCosmetic
                    GlowTeamManager.apply(player, cosmetic?.effect as? EffectCosmetic.Effect.Glow)
                }
                CosmeticType.BACKPACK ->
                    BackPassengerRenderer.setBack(
                        player,
                        wantedId?.let { registry.get(it) } as? BackpackCosmetic,
                    )
                // ITEM_SKIN renders through Polymer's ITEM_MODIFICATION_EVENT on the held tool —
                // nothing to sync per-player here, the cosmetic isn't tracked in `equipped`.
                CosmeticType.ITEM_SKIN -> Unit
                // Placeholder categories — no renderable cosmetics yet.
                CosmeticType.BALLOON,
                CosmeticType.POKEMON -> Unit
            }
        }
    }

    private fun tickPlayer(player: ServerPlayerEntity) {
        val data = DexCosmetics.playerData.get(player.uuid) ?: return
        for ((type, id) in data.equipped.snapshot()) {
            if (!data.owns(id)) continue
            val cosmetic = registry.get(id) ?: continue
            when (type) {
                CosmeticType.EFFECT ->
                    (cosmetic as? EffectCosmetic)?.let { EffectApplier.tick(player, it) }
                CosmeticType.BACKPACK -> BackPassengerRenderer.tick(player)
                // HAT broadcasts on its own cadence in HatEquipmentBroadcaster.tick.
                // ITEM_SKIN doesn't tick.
                else -> Unit
            }
        }
    }
}
