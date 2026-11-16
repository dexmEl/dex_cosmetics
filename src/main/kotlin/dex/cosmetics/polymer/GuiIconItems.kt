package dex.cosmetics.polymer

import dex.cosmetics.DexCosmetics
import eu.pb4.polymer.resourcepack.api.PolymerModelData
import java.util.EnumMap
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier

/**
 * Custom GUI icon items. Each is a CMD variant on PAPER (separate carrier from cosmetic items so
 * CMDs never collide). Textures ship in the mod jar; models emitted by [PackModelGenerator] at
 * pack-build time.
 */
object GuiIconItems {

    enum class Icon(val textureName: String) {
        BACK("back"),
        STAR_EMPTY("star_empty"),
        STAR_FILLED("star_filled"),
        TRANSPARENT("transparent"),
        HAT_ICON("hat_icon");

        val modelId: Identifier
            get() = DexCosmetics.id("item/$textureName")

        val textureId: Identifier
            get() = DexCosmetics.id("item/$textureName")
    }

    private val CARRIER = Items.PAPER
    private val models: MutableMap<Icon, PolymerModelData> = EnumMap(Icon::class.java)

    fun register() {
        models.clear()
        for (icon in Icon.entries) {
            models[icon] = CosmeticItems.allocate(CARRIER, icon.modelId)
        }
        DexCosmetics.LOGGER.info("Registered {} GUI icon models", models.size)
    }

    fun stack(icon: Icon): ItemStack {
        val stack = ItemStack(CARRIER)
        val cmd = models[icon]?.value() ?: return stack
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(cmd))
        return stack
    }

    fun allIcons(): List<Icon> = Icon.entries.toList()
}
