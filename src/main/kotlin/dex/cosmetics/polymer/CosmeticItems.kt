package dex.cosmetics.polymer

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.types.ItemSkinCosmetic
import eu.pb4.polymer.resourcepack.api.PolymerModelData
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/**
 * Owns the cosmetic → CMD allocations Polymer manages.
 *
 * Two carrier types in play:
 * - GUI icons + the cosmetic-helmet equipment item: always [CARRIER_ITEM] (LEATHER).
 * - Item-skin overrides: the actual vanilla item the skin targets (e.g. each sword variant), stored
 *   in [skinModelByPair] keyed by (skinId, itemId).
 *
 * CMDs we allocate start at [CosmeticsConfig.customModelDataStart] per item; [PolymerCompatibility]
 * retains the allocations across reloads. Must run BEFORE Polymer builds the resource pack (server
 * startup or `/cosmetics reload`).
 */
object CosmeticItems {
    /** LEATHER has no armor/attack/durability tooltip side-effects on its own. */
    private val CARRIER_ITEM: Item = Items.LEATHER

    private val baseModelByCosmeticId = ConcurrentHashMap<String, PolymerModelData>()
    private val skinModelByPair = ConcurrentHashMap<SkinKey, PolymerModelData>()
    private val cosmeticIdToSkinItems = ConcurrentHashMap<String, MutableSet<Identifier>>()

    private data class SkinKey(val cosmeticId: String, val itemId: Identifier)

    /** Rebuilds active lookups while retaining process-lifetime model allocations. */
    fun registerAll(registry: CosmeticRegistry) {
        baseModelByCosmeticId.clear()
        skinModelByPair.clear()
        cosmeticIdToSkinItems.clear()

        for (cosmetic in registry.all().sortedBy { it.id }) {
            registerBase(cosmetic)
            if (cosmetic is ItemSkinCosmetic) registerSkinVariants(cosmetic)
        }
        DexCosmetics.LOGGER.info(
            "Registered Polymer models for {} cosmetics ({} skin variants) starting at CMD {}",
            baseModelByCosmeticId.size,
            skinModelByPair.size,
            DexCosmetics.config.customModelDataStart.coerceAtLeast(1),
        )
    }

    private fun registerBase(cosmetic: Cosmetic) {
        baseModelByCosmeticId[cosmetic.id] = allocate(CARRIER_ITEM, cosmetic.modelId)
    }

    private fun registerSkinVariants(skin: ItemSkinCosmetic) {
        val seenItems = cosmeticIdToSkinItems.computeIfAbsent(skin.id) { mutableSetOf() }
        for (itemId in skin.compatibleItemIds.sortedBy { it.toString() }) registerSkinFor(
            skin,
            itemId,
            seenItems,
        )
        for (tag in skin.compatibleTags.sortedBy { it.id.toString() }) {
            for (item in Registries.ITEM) {
                if (!Registries.ITEM.getEntry(item).isIn(tag)) continue
                registerSkinFor(skin, Registries.ITEM.getId(item), seenItems)
            }
        }
    }

    private fun registerSkinFor(
        skin: ItemSkinCosmetic,
        itemId: Identifier,
        seen: MutableSet<Identifier>,
    ) {
        if (!seen.add(itemId)) return
        val item = Registries.ITEM.get(itemId)
        if (item == Items.AIR) {
            DexCosmetics.LOGGER.warn("Skin '{}' references unknown item '{}'", skin.id, itemId)
            return
        }
        skinModelByPair[SkinKey(skin.id, itemId)] = allocate(item, skin.modelId)
    }

    internal fun allocate(item: Item, modelId: Identifier): PolymerModelData =
        PolymerCompatibility.allocate(item, modelId)

    /** Stack used for GUI icons and the cosmetic-helmet item sent via fake-equipment packets. */
    fun iconStack(cosmetic: Cosmetic): ItemStack {
        val stack = ItemStack(CARRIER_ITEM)
        val baseModel = baseModelByCosmeticId[cosmetic.id]
        val cmd =
            if (cosmetic is ItemSkinCosmetic) {
                val itemId = Registries.ITEM.getId(CARRIER_ITEM)
                skinModelByPair[SkinKey(cosmetic.id, itemId)]?.value() ?: baseModel?.value()
            } else {
                baseModel?.value()
            }
        if (cmd != null) {
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(cmd))
        }
        // Override the carrier's vanilla name ("Leather") with the cosmetic's display_name so it
        // shows correctly when hovered out-of-GUI (e.g. in the helmet slot via fake equipment).
        stack.set(DataComponentTypes.CUSTOM_NAME, dex.cosmetics.util.Texts.mm(cosmetic.displayName))
        return stack
    }

    /** Look up the skin CMD for a given (cosmetic, held item). Returns null if not registered. */
    fun skinCmdFor(cosmeticId: String, heldItem: Item): Int? {
        val itemId = Registries.ITEM.getId(heldItem)
        return skinModelByPair[SkinKey(cosmeticId, itemId)]?.value()
    }
}
