package dex.cosmetics.polymer

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound

/**
 * Marks an item stack with a cosmetic skin id, stored in vanilla `minecraft:custom_data` —
 * registering our own DataComponentType would fail the registry-sync handshake with vanilla
 * clients. `custom_data` is already on every client and tolerates unknown NBT keys.
 */
object AppliedSkinComponent {
    private const val NBT_KEY = "dex_cosmetics_applied_skin"

    fun apply(stack: ItemStack, cosmeticId: String) {
        val existing = stack.get(DataComponentTypes.CUSTOM_DATA)
        val nbt = existing?.copyNbt() ?: NbtCompound()
        nbt.putString(NBT_KEY, cosmeticId)
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
    }

    fun read(stack: ItemStack): String? {
        val data = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return null
        val nbt = data.copyNbt()
        return if (nbt.contains(NBT_KEY)) nbt.getString(NBT_KEY) else null
    }

    fun remove(stack: ItemStack) {
        val existing = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return
        val nbt = existing.copyNbt()
        nbt.remove(NBT_KEY)
        if (nbt.isEmpty) {
            stack.remove(DataComponentTypes.CUSTOM_DATA)
        } else {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
        }
    }
}
