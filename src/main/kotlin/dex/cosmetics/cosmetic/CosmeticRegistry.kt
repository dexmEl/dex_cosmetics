package dex.cosmetics.cosmetic

/** Replaces a complete validated snapshot instead of clearing a live registry during reload. */
class CosmeticRegistry {
    @Volatile private var byId: Map<String, Cosmetic> = emptyMap()

    fun replaceAll(cosmetics: Collection<Cosmetic>) {
        val snapshot = cosmetics.sortedBy { it.id }.associateBy { it.id }
        require(snapshot.size == cosmetics.size) { "Duplicate cosmetic IDs" }
        byId = snapshot
    }

    fun get(id: String): Cosmetic? = byId[id]

    fun all(): Collection<Cosmetic> = byId.values

    fun ofType(type: CosmeticType): Collection<Cosmetic> = byId.values.filter { it.type == type }

    fun size(): Int = byId.size
}
