package dex.cosmetics.cosmetic

import net.minecraft.util.Identifier

abstract class Cosmetic {
    abstract val id: String
    abstract val type: CosmeticType
    abstract val displayName: String
    abstract val description: List<String>
    abstract val modelId: Identifier
    abstract val textureId: Identifier
    abstract val modelParent: Identifier
}
