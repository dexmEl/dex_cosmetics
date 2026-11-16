package dex.cosmetics.render

import dex.cosmetics.cosmetic.types.BackpackCosmetic
import dex.cosmetics.polymer.CosmeticItems
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld

/**
 * Renders BACK cosmetics via an invisible marker [ArmorStandEntity] mounted as a passenger,
 * cosmetic item in the HEAD slot. Same pattern HMCCosmetics uses — vanilla armor-stand head
 * rendering handles position + body-yaw tracking. Despawns during horizontal-body poses
 * (swim/elytra/sleep/spin) and respawns on pose exit.
 */
object BackPassengerRenderer {

    private val HIDE_POSES =
        setOf(
            EntityPose.SWIMMING,
            EntityPose.FALL_FLYING,
            EntityPose.SLEEPING,
            EntityPose.SPIN_ATTACK,
        )

    private val entries = ConcurrentHashMap<UUID, Entry>()

    /** Cosmetic stashed while the player is in a hide-pose so we can respawn it on exit. */
    private val parked = ConcurrentHashMap<UUID, BackpackCosmetic>()

    private data class Entry(val armorStand: ArmorStandEntity, val cosmetic: BackpackCosmetic)

    fun setBack(player: ServerPlayerEntity, cosmetic: BackpackCosmetic?) {
        despawn(player)
        parked.remove(player.uuid)
        if (cosmetic == null) return
        if (player.pose in HIDE_POSES) {
            parked[player.uuid] = cosmetic
            return
        }
        spawnFor(player, cosmetic)
    }

    fun clear(player: ServerPlayerEntity) {
        parked.remove(player.uuid)
        despawn(player)
    }

    fun despawn(player: ServerPlayerEntity) {
        val entry = entries.remove(player.uuid) ?: return
        entry.armorStand.stopRiding()
        entry.armorStand.discard()
    }

    fun tick(player: ServerPlayerEntity) {
        val hidden = player.pose in HIDE_POSES
        val current = entries[player.uuid]

        if (hidden && current != null) {
            parked[player.uuid] = current.cosmetic
            despawn(player)
            return
        }
        if (!hidden) {
            val parkedCosmetic = parked.remove(player.uuid)
            if (parkedCosmetic != null && current == null) {
                spawnFor(player, parkedCosmetic)
                return
            }
        }
        if (current != null) {
            val yaw = player.bodyYaw
            current.armorStand.yaw = yaw
            current.armorStand.bodyYaw = yaw
            current.armorStand.headYaw = yaw
        }
    }

    private fun spawnFor(player: ServerPlayerEntity, cosmetic: BackpackCosmetic) {
        val world = player.world as ServerWorld
        val stand =
            ArmorStandEntity(world, player.x, player.y, player.z).apply {
                isInvulnerable = true
                setNoGravity(true)
                // setMarker / setNoBasePlate are package-private; route every flag through NBT.
                // readCustomDataFromNbt resets unspecified booleans to false, so set Invisible here
                // too.
                readCustomDataFromNbt(
                    NbtCompound().apply {
                        putBoolean("Invisible", true)
                        putBoolean("Marker", true)
                        putBoolean("NoBasePlate", true)
                    }
                )
                equipStack(EquipmentSlot.HEAD, CosmeticItems.iconStack(cosmetic))
                yaw = player.bodyYaw
                bodyYaw = player.bodyYaw
                headYaw = player.bodyYaw
            }
        // SCALE attribute scales the whole entity including the head item. Uniform only.
        stand.getAttributeInstance(EntityAttributes.GENERIC_SCALE)?.baseValue =
            cosmetic.scale.x.toDouble()
        // Mount BEFORE spawn — avoids the SetPassengers-before-SpawnEntity packet race.
        stand.startRiding(player, true)
        world.spawnEntity(stand)
        entries[player.uuid] = Entry(stand, cosmetic)
    }
}
