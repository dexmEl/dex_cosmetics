package dex.cosmetics.cosmetic

import dex.cosmetics.cosmetic.types.ItemSkinCosmetic
import dex.cosmetics.data.PlayerDataManager
import dex.cosmetics.polymer.AppliedSkinComponent
import java.util.UUID
import java.util.function.Predicate
import net.minecraft.item.ItemStack

/** Shared rules for commands, menus, and player lifecycle events. Called on the server thread. */
class CosmeticService(
    private val registry: CosmeticRegistry,
    private val data: PlayerDataManager,
    private val refresh: (UUID) -> Unit,
) {
    fun grant(uuid: UUID, cosmeticId: String): Boolean {
        requireNotNull(registry.get(cosmeticId)) { "Unknown cosmetic: $cosmeticId" }
        return data.grant(uuid, cosmeticId)
    }

    fun revoke(uuid: UUID, cosmeticId: String): Boolean {
        val changed = data.revoke(uuid, cosmeticId)
        if (changed) refresh(uuid)
        return changed
    }

    fun equip(uuid: UUID, type: CosmeticType, cosmeticId: String?) {
        if (cosmeticId != null) {
            val cosmetic =
                requireNotNull(registry.get(cosmeticId)) { "This cosmetic is no longer available." }
            require(cosmetic.type == type && type != CosmeticType.ITEM_SKIN) {
                "Invalid cosmetic slot."
            }
            require(data.getOrLoad(uuid).owns(cosmeticId)) { "You don't own this cosmetic." }
        }
        data.equip(uuid, type, cosmeticId)
        refresh(uuid)
    }

    fun toggle(uuid: UUID, cosmeticId: String) {
        val cosmetic =
            requireNotNull(registry.get(cosmeticId)) { "This cosmetic is no longer available." }
        val equipped = data.getOrLoad(uuid).equipped[cosmetic.type]
        equip(uuid, cosmetic.type, if (equipped == cosmeticId) null else cosmeticId)
    }

    fun applySkin(uuid: UUID, cosmeticId: String, stack: ItemStack) {
        val skin =
            registry.get(cosmeticId) as? ItemSkinCosmetic
                ?: throw IllegalArgumentException("This skin is no longer available.")
        require(data.getOrLoad(uuid).owns(cosmeticId)) { "You don't own this skin." }
        require(skin.matches(stack)) { "Hold a compatible item to apply this skin." }
        AppliedSkinComponent.apply(stack, cosmeticId)
    }

    fun removeSkin(stack: ItemStack): Boolean {
        if (AppliedSkinComponent.read(stack) == null) return false
        AppliedSkinComponent.remove(stack)
        return true
    }

    /** Only the items actually inserted lose their skin; a ground remainder keeps its data. */
    fun insertPickedUpStack(
        uuid: UUID,
        stack: ItemStack,
        allowSharing: Boolean,
        insert: Predicate<ItemStack>,
    ): Boolean {
        val skinId = AppliedSkinComponent.read(stack)
        if (allowSharing || skinId == null || data.getOrLoad(uuid).owns(skinId)) {
            return insert.test(stack)
        }
        val pickedUp = stack.copy()
        AppliedSkinComponent.remove(pickedUp)
        val inserted = insert.test(pickedUp)
        stack.count = pickedUp.count
        return inserted
    }

    /** Drop stale slots after a reload or when loading an older database. Ownership is retained. */
    fun reconcile(uuid: UUID) {
        val player = data.getOrLoad(uuid)
        try {
            for ((type, id) in player.equipped.snapshot()) {
                if (
                    registry.get(id)?.type != type ||
                        !player.owns(id) ||
                        type == CosmeticType.ITEM_SKIN
                ) {
                    data.equip(uuid, type, null)
                }
            }
        } finally {
            refresh(uuid)
        }
    }
}
