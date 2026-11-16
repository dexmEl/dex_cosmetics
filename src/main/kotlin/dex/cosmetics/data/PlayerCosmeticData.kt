package dex.cosmetics.data

import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.cosmetic.EquippedCosmetics
import java.util.UUID

/** Accessed on the server thread. Mutations are coordinated by CosmeticService. */
class PlayerCosmeticData(val uuid: UUID) {
    private val owned = mutableSetOf<String>()
    val equipped = EquippedCosmetics()

    fun ownedSnapshot(): Set<String> = owned.toSet()

    fun owns(cosmeticId: String): Boolean = cosmeticId in owned

    internal fun grant(cosmeticId: String): Boolean = owned.add(cosmeticId)

    internal fun revoke(cosmeticId: String): Boolean {
        if (!owned.remove(cosmeticId)) return false
        for ((type, id) in equipped.snapshot()) {
            if (id == cosmeticId) equipped.set(type, null)
        }
        return true
    }

    internal fun equip(type: CosmeticType, cosmeticId: String?) = equipped.set(type, cosmeticId)

    internal fun loadOwned(ids: Collection<String>) {
        owned.clear()
        owned.addAll(ids)
    }
}
