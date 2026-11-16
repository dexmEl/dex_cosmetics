package dex.cosmetics.gui

import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.polymer.CosmeticItems
import dex.cosmetics.polymer.GuiIconItems
import dex.cosmetics.util.Texts
import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Items
import net.minecraft.text.Text

object GuiIcons {
    fun cosmeticIcon(cosmetic: Cosmetic, owned: Boolean, equipped: Boolean): GuiElementBuilder {
        val stack = CosmeticItems.iconStack(cosmetic)
        // Owned items show the author's full MiniMessage formatting; locked items are forced dark.
        val nameText =
            if (owned) {
                Texts.mm(cosmetic.displayName)
            } else {
                Texts.mm("<dark_gray>${cosmetic.displayName}</dark_gray>")
            }
        val builder = GuiElementBuilder.from(stack).setName(nameText)
        cosmetic.description.forEach { builder.addLoreLine(Texts.mm(it)) }
        if (!owned) builder.addLoreLine(Texts.mm("<red>Locked</red>"))
        if (equipped) builder.addLoreLine(Texts.mm("<green>Equipped</green>"))
        return builder
    }

    /**
     * A transparent, tooltip-suppressed paper item with a click callback. Used to overlay invisible
     * clickable regions on a GUI background image — the player sees only the background, but
     * clicking inside the region fires the callback.
     */
    fun invisibleClickable(onClick: () -> Unit): GuiElement {
        val stack = GuiIconItems.stack(GuiIconItems.Icon.TRANSPARENT)
        stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        return GuiElementBuilder.from(stack)
            .setName(Text.empty())
            .setCallback { _, _, _, _ -> onClick() }
            .build()
    }

    fun back(): GuiElementBuilder =
        GuiElementBuilder.from(GuiIconItems.stack(GuiIconItems.Icon.BACK))
            .setName(Texts.mm("<yellow>Back</yellow>"))

    /** Filter toggle. star_empty = show all cosmetics (default); star_filled = show owned only. */
    fun filterToggle(ownedOnly: Boolean): GuiElementBuilder {
        val icon = if (ownedOnly) GuiIconItems.Icon.STAR_FILLED else GuiIconItems.Icon.STAR_EMPTY
        val builder = GuiElementBuilder.from(GuiIconItems.stack(icon))
        if (ownedOnly) {
            builder.setName(Texts.mm("<green>Showing: Owned Only</green>"))
            builder.addLoreLine(Texts.mm("<gray>Click to show all cosmetics</gray>"))
        } else {
            builder.setName(Texts.mm("<aqua>Showing: All Cosmetics</aqua>"))
            builder.addLoreLine(Texts.mm("<gray>Click to show only owned</gray>"))
        }
        return builder
    }

    /** Display the cosmetic the player currently has equipped in this category. */
    fun equippedDisplay(cosmetic: Cosmetic): GuiElementBuilder {
        val stack = CosmeticItems.iconStack(cosmetic)
        val builder =
            GuiElementBuilder.from(stack)
                .setName(Texts.mm("<green>Equipped: </green>${cosmetic.displayName}"))
        cosmetic.description.forEach { builder.addLoreLine(Texts.mm(it)) }
        builder.addLoreLine(Texts.mm("<red>Click to unequip</red>"))
        return builder
    }

    /**
     * Placeholder shown in the "currently equipped" slot when the player has nothing equipped in
     * this category. Non-interactive — just a visual prompt.
     */
    fun equippedSlotEmpty(type: CosmeticType): GuiElementBuilder {
        val builder =
            when (type) {
                CosmeticType.HAT ->
                    GuiElementBuilder.from(GuiIconItems.stack(GuiIconItems.Icon.HAT_ICON))
                CosmeticType.BACKPACK -> GuiElementBuilder(Items.CHEST)
                CosmeticType.EFFECT -> GuiElementBuilder(Items.FIREWORK_STAR)
                CosmeticType.ITEM_SKIN -> GuiElementBuilder(Items.NETHERITE_SWORD)
                CosmeticType.BALLOON -> GuiElementBuilder(Items.WHITE_WOOL)
                CosmeticType.POKEMON -> GuiElementBuilder(Items.SLIME_BALL)
            }
        return builder.setName(
            Texts.mm("<yellow>Select a cosmetic from the menu to equip it!</yellow>")
        )
    }
}
