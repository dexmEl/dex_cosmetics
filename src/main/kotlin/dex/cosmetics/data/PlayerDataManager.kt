package dex.cosmetics.data

import dex.cosmetics.cosmetic.CosmeticType
import java.util.UUID

/** Main-thread cache. Writes commit before memory changes; failures propagate to the caller. */
class PlayerDataManager(private val storage: PlayerDataStorage) {
    private val cache = mutableMapOf<UUID, PlayerCosmeticData>()

    fun get(uuid: UUID): PlayerCosmeticData? = cache[uuid]

    fun getOrLoad(uuid: UUID): PlayerCosmeticData =
        cache.getOrPut(uuid) {
            PlayerCosmeticData(uuid).apply {
                loadOwned(storage.loadOwned(uuid))
                equipped.loadFrom(storage.loadEquipped(uuid))
            }
        }

    fun onQuit(uuid: UUID) {
        cache.remove(uuid)
    }

    internal fun grant(uuid: UUID, cosmeticId: String): Boolean {
        val data = getOrLoad(uuid)
        if (data.owns(cosmeticId)) return false
        storage.grant(uuid, cosmeticId)
        return data.grant(cosmeticId)
    }

    internal fun revoke(uuid: UUID, cosmeticId: String): Boolean {
        val data = getOrLoad(uuid)
        if (!data.owns(cosmeticId)) return false
        storage.revoke(uuid, cosmeticId)
        return data.revoke(cosmeticId)
    }

    internal fun equip(uuid: UUID, type: CosmeticType, cosmeticId: String?) {
        val data = getOrLoad(uuid)
        storage.setEquipped(uuid, type, cosmeticId)
        data.equip(type, cosmeticId)
    }

    fun shutdown() {
        storage.close()
        cache.clear()
    }
}
