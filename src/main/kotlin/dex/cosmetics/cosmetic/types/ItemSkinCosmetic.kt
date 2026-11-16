package dex.cosmetics.cosmetic.types

import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

data class ItemSkinCosmetic(
    override val id: String,
    override val displayName: String,
    override val description: List<String>,
    override val modelId: Identifier,
    override val textureId: Identifier,
    override val modelParent: Identifier,
    val compatibleItemIds: Set<Identifier>,
    val compatibleTags: Set<TagKey<Item>>,
) : Cosmetic() {
    override val type: CosmeticType = CosmeticType.ITEM_SKIN

    fun matches(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val itemId = Registries.ITEM.getId(stack.item)
        if (itemId in compatibleItemIds) return true
        val registryEntry = Registries.ITEM.getEntry(stack.item)
        return compatibleTags.any { registryEntry.isIn(it) }
    }

    companion object {
        fun parseCompatible(raw: List<String>): Pair<Set<Identifier>, Set<TagKey<Item>>> {
            val ids = mutableSetOf<Identifier>()
            val tags = mutableSetOf<TagKey<Item>>()
            for (entry in raw) {
                if (entry.startsWith("#")) {
                    val tagId = Identifier.tryParse(entry.substring(1)) ?: continue
                    tags += TagKey.of(RegistryKeys.ITEM, tagId)
                } else {
                    Identifier.tryParse(entry)?.let { ids += it }
                }
            }
            return ids to tags
        }
    }
}
