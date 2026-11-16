package dex.cosmetics.gui

import dex.cosmetics.cosmetic.CosmeticType
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity

/**
 * The top-level cosmetics menu: a config-driven 3-card carousel. The whole page (focused card + two
 * preview cards + chrome) is a single generated glyph in the title; the slots are invisible click
 * zones ([MenuLayout]). Clicking the centre card opens that category's browser; the side arrows
 * play a generated transition strip and re-open this page focused on the new category.
 *
 * Replaces the old per-page classes — the carousel order and contents come entirely from [MenuRing]
 * (config `menu_categories` / `menu_focus`).
 */
class MenuPageGui(player: ServerPlayerEntity, private val ring: MenuRing, private val focus: Int) :
    SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false) {

    private val glyphs = MenuGlyphs(ring)

    init {
        title = GuiBackground.menuImage(glyphs.rest(focus))
        populate()
    }

    private fun populate() {
        val openBrowser =
            GuiIcons.invisibleClickable {
                val gui = browserFor(ring[focus], player)
                close(true)
                gui.refresh()
                gui.open()
            }
        for (slot in MenuLayout.CENTER_SLOTS) setSlot(slot, openBrowser)

        // Left arrow (<): bring the LEFT neighbour to focus — cards slide right (cycle-right
        // strip).
        val toLeftNeighbour =
            GuiIcons.invisibleClickable {
                close(true)
                TransitionGui.play(player, glyphs.cycleRightFrames(focus)) { p ->
                    MenuPageGui(p, ring, ring.afterCycleRight(focus))
                }
            }
        for (slot in MenuLayout.LEFT_ARROW_SLOTS) setSlot(slot, toLeftNeighbour)

        // Right arrow (>): bring the RIGHT neighbour to focus — cards slide left (cycle-left
        // strip).
        val toRightNeighbour =
            GuiIcons.invisibleClickable {
                close(true)
                TransitionGui.play(player, glyphs.cycleLeftFrames(focus)) { p ->
                    MenuPageGui(p, ring, ring.afterCycleLeft(focus))
                }
            }
        for (slot in MenuLayout.RIGHT_ARROW_SLOTS) setSlot(slot, toRightNeighbour)
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

    companion object {
        /** Open the menu focused on the configured default category. */
        fun open(player: ServerPlayerEntity) {
            val ring = MenuRing.fromConfig()
            MenuPageGui(player, ring, ring.defaultFocus).open()
        }

        /** Open the menu focused on [type] (e.g. returning from that category's browser). */
        fun openFocused(player: ServerPlayerEntity, type: CosmeticType) {
            val ring = MenuRing.fromConfig()
            val index = ring.indexOf(type).let { if (it >= 0) it else ring.defaultFocus }
            MenuPageGui(player, ring, index).open()
        }

        private fun browserFor(type: CosmeticType, player: ServerPlayerEntity): CosmeticBrowserGui =
            when (type) {
                CosmeticType.ITEM_SKIN -> ItemSkinBrowserGui(player)
                else -> CosmeticBrowserGui(player, type)
            }
    }
}
