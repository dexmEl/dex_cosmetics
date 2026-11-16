package dex.cosmetics.cosmetic

enum class CosmeticType(val configKey: String, val dirName: String, val displayName: String) {
    HAT("HAT", "hats", "Hats"),
    BACKPACK("BACK", "back", "Back Items"),
    ITEM_SKIN("ITEM", "items", "Item Skins"),
    EFFECT("EFFECT", "effects", "Effects"),
    // Placeholder categories: UI/menu only for now, no cosmetic behavior yet. Off by default in
    // CosmeticsConfig.menuCategories. Defining cosmetics for these is future work — buildCosmetic
    // skips them, so any such cosmetic JSON is ignored with a warning.
    BALLOON("BALLOON", "balloons", "Balloons"),
    POKEMON("POKEMON", "pokemon", "Pokemon");

    companion object {
        fun fromConfigKey(key: String): CosmeticType? =
            entries.firstOrNull { it.configKey.equals(key, ignoreCase = true) }

        fun fromDirName(dir: String): CosmeticType? =
            entries.firstOrNull { it.dirName.equals(dir, ignoreCase = true) }
    }
}
