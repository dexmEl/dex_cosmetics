package dex.cosmetics.gui

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.util.Texts
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Item-skin browser. Inherits the filter toggle and full layout from [CosmeticBrowserGui]; on top
 * of that:
 * - The grid shows every item-skin cosmetic regardless of what the player is holding —
 *   compatibility is enforced at click-time instead, so players can browse the catalog without
 *   first equipping a compatible tool.
 * - Clicking an owned skin stamps the AppliedSkin data component onto the held item; if the held
 *   item isn't compatible (or the player isn't holding anything), the click fails with a hint to
 *   hold a compatible item.
 */
class ItemSkinBrowserGui(player: ServerPlayerEntity) :
    CosmeticBrowserGui(player, CosmeticType.ITEM_SKIN) {

    override fun handleClick(cosmetic: Cosmetic) {
        perform {
            DexCosmetics.service.applySkin(player.uuid, cosmetic.id, player.mainHandStack)
            player.sendMessage(
                Texts.mm(
                    "<green>Applied skin: </green>${DexCosmetics.registry.get(cosmetic.id)?.displayName}"
                ),
                false,
            )
        }
    }
}
