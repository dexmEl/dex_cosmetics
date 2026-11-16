package dex.cosmetics.gui

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.util.Texts
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
import kotlin.math.max
import kotlin.math.min
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Browser layout shared by all 4 cosmetic types (9x6 = 54 slots). Subclasses can re-position
 * individual elements via the `protected open val` slot overrides below.
 * - [filterSlot] → filter toggle (All Cosmetics / Owned Only)
 * - slots [GRID_START]..[GRID_START]+[PAGE_SIZE]-1 → cosmetic icons (3 rows × 9)
 * - [prevSlots] → page-back invisible click region (only when page > 0; 2 slots)
 * - [equippedSlot] → currently-equipped icon
 * - [nextSlots] → page-next invisible click region (only when more pages exist; 2 slots)
 * - [backToMainSlot] → back to carousel page
 *
 * The title is set in [refresh] so it varies with page state — each browser type has 4 background
 * variants (no-arrows / right-arrow / both-arrows / left-arrow) picked by [titleGlyph] based on
 * (currentPage, totalPages). When the gui is already open, sgui's `setTitle` re-sends
 * `OpenScreenS2CPacket` with the same syncId so the title updates in place without flicker.
 */
open class CosmeticBrowserGui(player: ServerPlayerEntity, val cosmeticType: CosmeticType) :
    eu.pb4.sgui.api.gui.SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false) {

    companion object {
        const val GRID_START = 0
        const val PAGE_SIZE = 27
    }

    // Slot positions (0-indexed). 1-indexed spec: prev pg 46-47, filter 48, equipped 50,
    // back 52, next pg 53-54. Subclasses override only if they need a different layout.
    protected open val filterSlot: Int = 47
    protected open val equippedSlot: Int = 49
    protected open val backToMainSlot: Int = 51
    protected open val prevSlots: List<Int> = listOf(45, 46)
    protected open val nextSlots: List<Int> = listOf(52, 53)

    protected var page: Int = 0
    protected var ownedOnly: Boolean = false

    init {
        // Set a sensible initial title before refresh() runs (in case .open() is called without
        // refresh, though current call sites always refresh first).
        title = GuiBackground.imageOnly(titleGlyph(0, 1))
    }

    /**
     * Picks the background glyph based on pagination state. Each type ships 4 variants: base (no
     * arrows), right-arrow (first of N), both-arrows (middle), left-arrow (last). Override only if
     * a specific subclass wants different per-page art.
     */
    protected open fun titleGlyph(currentPage: Int, totalPages: Int): String {
        val variants = variantsForType(cosmeticType)
        return when {
            totalPages <= 1 -> variants.base
            currentPage == 0 -> variants.right
            currentPage == totalPages - 1 -> variants.left
            else -> variants.both
        }
    }

    /**
     * Visual element placed on each [prevSlots] when a previous page exists. Default: invisible.
     */
    protected open fun makePrevElement(onClick: () -> Unit): GuiElement =
        GuiIcons.invisibleClickable(onClick)

    /** Visual element placed on each [nextSlots] when a next page exists. Default: invisible. */
    protected open fun makeNextElement(onClick: () -> Unit): GuiElement =
        GuiIcons.invisibleClickable(onClick)

    /** The 4-variant glyph set for a given browser type — base / right / both / left. */
    private data class Variants(
        val base: String,
        val right: String,
        val both: String,
        val left: String,
    )

    private fun variantsForType(type: CosmeticType): Variants =
        when (type) {
            CosmeticType.HAT ->
                Variants(
                    GuiBackground.Glyph.HATS,
                    GuiBackground.Glyph.HATS_RIGHT_ARROW,
                    GuiBackground.Glyph.HATS_BOTH_ARROWS,
                    GuiBackground.Glyph.HATS_LEFT_ARROW,
                )
            CosmeticType.ITEM_SKIN ->
                Variants(
                    GuiBackground.Glyph.ITEMS,
                    GuiBackground.Glyph.ITEMS_RIGHT_ARROW,
                    GuiBackground.Glyph.ITEMS_BOTH_ARROWS,
                    GuiBackground.Glyph.ITEMS_LEFT_ARROW,
                )
            CosmeticType.EFFECT ->
                Variants(
                    GuiBackground.Glyph.EFFECTS,
                    GuiBackground.Glyph.EFFECTS_RIGHT_ARROW,
                    GuiBackground.Glyph.EFFECTS_BOTH_ARROWS,
                    GuiBackground.Glyph.EFFECTS_LEFT_ARROW,
                )
            CosmeticType.BACKPACK ->
                Variants(
                    GuiBackground.Glyph.BACK,
                    GuiBackground.Glyph.BACK_RIGHT_ARROW,
                    GuiBackground.Glyph.BACK_BOTH_ARROWS,
                    GuiBackground.Glyph.BACK_LEFT_ARROW,
                )
            CosmeticType.BALLOON ->
                Variants(
                    GuiBackground.Glyph.BALLOONS,
                    GuiBackground.Glyph.BALLOONS_RIGHT_ARROW,
                    GuiBackground.Glyph.BALLOONS_BOTH_ARROWS,
                    GuiBackground.Glyph.BALLOONS_LEFT_ARROW,
                )
            CosmeticType.POKEMON ->
                Variants(
                    GuiBackground.Glyph.POKEMON,
                    GuiBackground.Glyph.POKEMON_RIGHT_ARROW,
                    GuiBackground.Glyph.POKEMON_BOTH_ARROWS,
                    GuiBackground.Glyph.POKEMON_LEFT_ARROW,
                )
        }

    protected open fun cosmetics(): List<Cosmetic> {
        val all = DexCosmetics.registry.ofType(cosmeticType).sortedBy { it.displayName }
        if (!ownedOnly) return all
        val data = DexCosmetics.playerData.get(player.uuid) ?: return emptyList()
        return all.filter { data.owns(it.id) }
    }

    open fun refresh() {
        // Wipe every slot so stale icons from a prior page/filter don't leak through.
        for (i in 0 until size) clearSlot(i)

        val items = cosmetics()
        val pages = max(1, (items.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pages - 1)

        title = GuiBackground.imageOnly(titleGlyph(page, pages))

        val from = page * PAGE_SIZE
        val to = min(from + PAGE_SIZE, items.size)

        val data = DexCosmetics.playerData.get(player.uuid)
        val currentlyEquippedId = data?.equipped?.get(cosmeticType)

        setSlot(
            filterSlot,
            GuiIcons.filterToggle(ownedOnly).setCallback { _, _, _, _ ->
                ownedOnly = !ownedOnly
                page = 0
                refresh()
            },
        )

        for (i in 0 until (to - from)) {
            val c = items[from + i]
            val owned = data?.owns(c.id) == true
            val isEquipped = currentlyEquippedId == c.id
            setSlot(
                GRID_START + i,
                GuiIcons.cosmeticIcon(c, owned, isEquipped).setCallback { _, _, _, _ ->
                    handleClick(c)
                },
            )
        }

        if (page > 0) {
            val prevElem = makePrevElement {
                page--
                refresh()
            }
            for (slot in prevSlots) setSlot(slot, prevElem)
        }

        val equippedCosmetic = currentlyEquippedId?.let { DexCosmetics.registry.get(it) }
        if (equippedCosmetic != null) {
            setSlot(
                equippedSlot,
                GuiIcons.equippedDisplay(equippedCosmetic).setCallback { _, _, _, _ ->
                    perform { DexCosmetics.service.equip(player.uuid, cosmeticType, null) }
                },
            )
        } else if (cosmeticType != CosmeticType.ITEM_SKIN) {
            setSlot(equippedSlot, GuiIcons.equippedSlotEmpty(cosmeticType))
        }

        if (to < items.size) {
            val nextElem = makeNextElement {
                page++
                refresh()
            }
            for (slot in nextSlots) setSlot(slot, nextElem)
        }

        setSlot(
            backToMainSlot,
            GuiIcons.back().setCallback { _, _, _, _ ->
                close(true)
                MenuPageGui.openFocused(player, cosmeticType)
            },
        )
    }

    override fun onClick(
        index: Int,
        type: ClickType,
        action: SlotActionType,
        element: GuiElementInterface?,
    ): Boolean {
        if (element != null) GuiSounds.playClick(player)
        return super.onClick(index, type, action, element)
    }

    protected open fun handleClick(cosmetic: Cosmetic) {
        perform { DexCosmetics.service.toggle(player.uuid, cosmetic.id) }
    }

    protected fun perform(action: () -> Unit) {
        try {
            action()
        } catch (e: IllegalArgumentException) {
            player.sendMessage(
                net.minecraft.text.Text.literal(e.message ?: "Invalid cosmetic.")
                    .formatted(net.minecraft.util.Formatting.RED),
                false,
            )
        } catch (e: Exception) {
            DexCosmetics.LOGGER.error("Cosmetics menu action failed for {}", player.uuid, e)
            player.sendMessage(
                Texts.mm("<red>Could not complete this change; see the server log.</red>"),
                false,
            )
        } finally {
            refresh()
        }
    }
}
