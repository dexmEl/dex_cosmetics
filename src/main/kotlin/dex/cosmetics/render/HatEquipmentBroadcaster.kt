package dex.cosmetics.render

import com.mojang.datafixers.util.Pair
import dex.cosmetics.cosmetic.types.HatCosmetic
import dex.cosmetics.polymer.CosmeticItems
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld

/** Slot index of the helmet in the player inventory screen handler. */
private const val PLAYER_INVENTORY_HELMET_SLOT = 5

/**
 * Renders HAT cosmetics by broadcasting fake [EntityEquipmentUpdateS2CPacket]s. Viewers see the
 * cosmetic in the wearer's helmet slot, so vanilla armor rendering paints it on the head bone (head
 * rotation, pitch shift, 60fps tracking come free). The wearer's real helmet stays put in their
 * inventory. Same pattern HMCCosmetics uses.
 *
 * Cadence: wearer gets the packet every tick (cheap, one recipient, prevents flicker when clicking
 * the helmet slot). Other viewers get it every [OTHERS_REBROADCAST_INTERVAL] ticks.
 */
object HatEquipmentBroadcaster {

    /** ~200ms, low enough to heal vanilla equipment packets without saturating tracker traffic. */
    private const val OTHERS_REBROADCAST_INTERVAL = 4

    private val equipped = ConcurrentHashMap<UUID, HatCosmetic>()
    private var tickCounter = 0

    fun setHat(player: ServerPlayerEntity, hat: HatCosmetic?) {
        if (hat == null) {
            if (equipped.remove(player.uuid) != null) {
                // Push the real helmet so the cosmetic visual goes away and any client-side
                // inventory desync from prior cosmetic packets corrects back.
                val real = player.getEquippedStack(EquipmentSlot.HEAD)
                sendToWearer(player, real)
                sendToOthers(player, real)
            }
            return
        }
        equipped[player.uuid] = hat
        broadcastFor(player, hat, toWearer = true, toOthers = true)
    }

    fun onPlayerJoin(player: ServerPlayerEntity) {
        val server = player.server
        for ((uuid, hat) in equipped) {
            if (uuid == player.uuid) continue
            val wearer = server.playerManager.getPlayer(uuid) ?: continue
            player.networkHandler.sendPacket(packetFor(wearer, CosmeticItems.iconStack(hat)))
        }
    }

    fun onPlayerQuit(player: ServerPlayerEntity) {
        equipped.remove(player.uuid)
    }

    fun tick(server: MinecraftServer) {
        tickCounter++
        val sendToOthersThisTick = tickCounter % OTHERS_REBROADCAST_INTERVAL == 0
        for ((uuid, hat) in equipped) {
            val player = server.playerManager.getPlayer(uuid) ?: continue
            broadcastFor(player, hat, toWearer = true, toOthers = sendToOthersThisTick)
        }
    }

    private fun broadcastFor(
        player: ServerPlayerEntity,
        hat: HatCosmetic,
        toWearer: Boolean,
        toOthers: Boolean,
    ) {
        val item = CosmeticItems.iconStack(hat)
        if (toWearer) sendToWearer(player, item)
        if (toOthers) sendToOthers(player, item)
    }

    /**
     * Mixin entry point after vanilla processes a slot click. Vanilla just reset the helmet slot to
     * its real (usually empty) state on the wearer's client; immediately re-broadcast the cosmetic
     * so the click feels like a no-op.
     */
    @JvmStatic
    fun refreshFor(player: ServerPlayerEntity) {
        val hat = equipped[player.uuid] ?: return
        broadcastFor(player, hat, toWearer = true, toOthers = false)
    }

    /**
     * Mixin entry point when the click landed specifically on the helmet slot — the only case where
     * the client's prediction would have moved the cosmetic onto the cursor. Sending an
     * empty-cursor packet unconditionally would wipe any item the player is legitimately holding.
     */
    @JvmStatic
    fun clearCursorFor(player: ServerPlayerEntity) {
        if (equipped[player.uuid] == null) return
        val screenHandler = player.playerScreenHandler
        player.networkHandler.sendPacket(
            ScreenHandlerSlotUpdateS2CPacket(-1, screenHandler.nextRevision(), -1, ItemStack.EMPTY)
        )
    }

    private fun sendToWearer(player: ServerPlayerEntity, item: ItemStack) {
        // Screen-handler slot packet drives the inventory screen visual; entity equipment packet
        // drives the F5 render. Both write the same backing slot but sending both avoids a frame
        // of empty-helmet during the swap.
        val screenHandler = player.playerScreenHandler
        player.networkHandler.sendPacket(
            ScreenHandlerSlotUpdateS2CPacket(
                screenHandler.syncId,
                screenHandler.nextRevision(),
                PLAYER_INVENTORY_HELMET_SLOT,
                item,
            )
        )
        player.networkHandler.sendPacket(packetFor(player, item))
    }

    private fun sendToOthers(player: ServerPlayerEntity, item: ItemStack) {
        val world = player.world as ServerWorld
        world.chunkManager.sendToOtherNearbyPlayers(player, packetFor(player, item))
    }

    private fun packetFor(
        player: ServerPlayerEntity,
        item: ItemStack,
    ): EntityEquipmentUpdateS2CPacket =
        EntityEquipmentUpdateS2CPacket(player.id, listOf(Pair.of(EquipmentSlot.HEAD, item)))
}
