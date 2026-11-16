package dex.cosmetics.config

import com.google.gson.annotations.SerializedName

data class CosmeticsConfig(
    @SerializedName("particle_tick_budget_per_player") val particleTickBudgetPerPlayer: Int = 32,
    @SerializedName("admin_permission") val adminPermission: String = "dexcosmetics.admin",

    /**
     * When `true` (default), an applied item skin travels with the item — if the wearer drops a
     * skinned sword and another player picks it up, the skin stays on the stack.
     *
     * When `false`, the [dex.cosmetics.polymer.AppliedSkinComponent] is stripped from a stack the
     * moment it's picked up by a player who doesn't own that cosmetic. The pickup itself still
     * succeeds — only the visual override is removed.
     */
    @SerializedName("allow_item_skin_sharing") val allowItemSkinSharing: Boolean = true,

    /**
     * When `true`, an item with an applied skin shows the cosmetic's `display_name` as its
     * hover/tooltip name (overriding the vanilla item name).
     *
     * When `false` (default), the vanilla item name is preserved — the skin only swaps the
     * model/texture, not the label. Name override is applied to the client-side copy at sync time,
     * so dropping the item or trading it doesn't permanently rename the underlying stack.
     */
    @SerializedName("rename_item_to_skin_name") val renameItemToSkinName: Boolean = false,

    /**
     * How the generated resource pack is delivered to clients.
     * - `"auto"` (default): polymer-autohost serves the pack over its built-in HTTP server and
     *   sends the URL to joining clients automatically. Standard behavior.
     * - `"manual"`: the pack is still generated to `polymer/resource_pack.zip` on startup and via
     *   `/cosmetics generatepack`, but polymer-autohost is disabled. The server admin is expected
     *   to host the zip themselves (e.g. CDN, web server) and point at it via `server.properties`
     *   `resource-pack=<URL>`.
     *
     * Only `auto` and `manual` are accepted. Changes require a server restart.
     */
    @SerializedName("resource_pack_mode") val resourcePackMode: String = "auto",

    /**
     * The starting `CustomModelData` integer for cosmetics this mod generates. Each carrier item
     * gets its own ascending counter from this base — so if you set 2000 and the mod needs 15
     * LEATHER variants, they occupy CMD 2000..2014 on LEATHER (and the same range independently on
     * every other vanilla item we touch, since CMD is per-item).
     *
     * Default is 2000 — high enough to clear most existing resource packs and item-NBT mods, low
     * enough that 32-bit overflow isn't a concern. Raise it if you have other mods already
     * occupying CMDs in this range.
     *
     * Must be in 1..1000000. Changing this value requires a server restart.
     */
    @SerializedName("custom_model_data_start") val customModelDataStart: Int = 2000,

    /**
     * Carousel-menu categories mapped to whether each is enabled — set `true` to show a category,
     * `false` to hide it. Keys are [dex.cosmetics.cosmetic.CosmeticType] config keys (HAT / ITEM /
     * EFFECT / BACK / BALLOON / POKEMON); unknown keys are ignored.
     *
     * The map's key order is the carousel's left-to-right ring order (JSON preserves key order).
     * It's a fill loop — for any focused card the previous enabled entry is its left neighbour, the
     * next is its right neighbour, and the one two slots away cycles in from off-screen — so keep
     * at least 4 enabled for a clean look. Changing this requires a server restart so client font
     * codepoints match.
     *
     * BALLOON and POKEMON are placeholder categories (menu/UI only, no cosmetics yet) — disabled by
     * default. Adding a brand-new category also needs a `gui/cards/<dir>.png` face + browser art.
     *
     * Default order with focus `HAT` renders: ITEMS left, HATS centre, BACK right.
     */
    @SerializedName("menu_categories")
    val menuCategories: Map<String, Boolean> =
        linkedMapOf(
            "ITEM" to true,
            "HAT" to true,
            "BACK" to true,
            "EFFECT" to true,
            "BALLOON" to false,
            "POKEMON" to false,
        ),

    /**
     * Which enabled [menuCategories] entry is centred (focused) when the menu opens. If
     * missing/unknown/disabled, the first enabled category is used.
     */
    @SerializedName("menu_focus") val menuFocus: String = "HAT",
) {
    fun validated(): CosmeticsConfig {
        require(resourcePackMode in setOf("auto", "manual")) {
            "resource_pack_mode must be auto or manual"
        }
        require(customModelDataStart in 1..1_000_000) {
            "custom_model_data_start must be in 1..1000000"
        }
        require(particleTickBudgetPerPlayer in 0..4096) {
            "particle_tick_budget_per_player must be in 0..4096"
        }
        require(adminPermission.isNotBlank()) { "admin_permission must not be blank" }
        require(menuCategories.keys.map { it.uppercase() }.distinct().size == menuCategories.size) {
            "Duplicate menu category keys"
        }
        return this
    }

    fun requireReloadCompatible(previous: CosmeticsConfig) {
        require(resourcePackMode == previous.resourcePackMode) {
            "resource_pack_mode changed; restart the server to apply it."
        }
        require(customModelDataStart == previous.customModelDataStart) {
            "custom_model_data_start changed; restart the server to apply it."
        }
        // Font codepoints are baked into each client's pack. Reordering needs a new connection.
        require(
            menuCategories == previous.menuCategories &&
                menuCategories.keys.toList() == previous.menuCategories.keys.toList()
        ) {
            "menu_categories changed; restart the server so clients receive matching menu fonts."
        }
    }
}
