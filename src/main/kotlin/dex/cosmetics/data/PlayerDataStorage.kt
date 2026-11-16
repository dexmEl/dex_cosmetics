package dex.cosmetics.data

import dex.cosmetics.cosmetic.CosmeticType
import java.util.UUID

interface PlayerDataStorage : AutoCloseable {
    fun open()

    fun loadOwned(uuid: UUID): Set<String>

    fun loadEquipped(uuid: UUID): Map<CosmeticType, String>

    fun grant(uuid: UUID, cosmeticId: String)

    fun revoke(uuid: UUID, cosmeticId: String)

    fun setEquipped(uuid: UUID, type: CosmeticType, cosmeticId: String?)

    override fun close()
}
