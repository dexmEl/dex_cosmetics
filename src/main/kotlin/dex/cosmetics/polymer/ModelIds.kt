package dex.cosmetics.polymer

/** Each carrier/model pair keeps its ID until restart, including temporarily removed models. */
internal class ModelIds(private val start: Int) {
    private val next = mutableMapOf<String, Int>()
    private val ids = mutableMapOf<Pair<String, String>, Int>()

    init {
        require(start in 1..1_000_000)
    }

    fun allocate(carrier: String, model: String): Int =
        ids.getOrPut(carrier to model) {
            val id = next.getOrDefault(carrier, start)
            check(id < Int.MAX_VALUE) { "CustomModelData exhausted for $carrier" }
            next[carrier] = id + 1
            id
        }
}
