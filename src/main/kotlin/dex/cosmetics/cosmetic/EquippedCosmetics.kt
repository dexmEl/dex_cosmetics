package dex.cosmetics.cosmetic

import java.util.EnumMap

class EquippedCosmetics {
    private val slots: MutableMap<CosmeticType, String> = EnumMap(CosmeticType::class.java)

    operator fun get(type: CosmeticType): String? = slots[type]

    fun set(type: CosmeticType, cosmeticId: String?) {
        if (cosmeticId == null) slots.remove(type) else slots[type] = cosmeticId
    }

    fun snapshot(): Map<CosmeticType, String> = EnumMap(slots)

    fun clear() = slots.clear()

    fun loadFrom(source: Map<CosmeticType, String>) {
        slots.clear()
        slots.putAll(source)
    }
}
