package dex.cosmetics.cosmetic.types

import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import net.minecraft.util.Identifier
import org.joml.Vector3f

data class HatCosmetic(
    override val id: String,
    override val displayName: String,
    override val description: List<String>,
    override val modelId: Identifier,
    override val textureId: Identifier,
    override val modelParent: Identifier,
    val offset: Vector3f,
    val scale: Vector3f,
) : Cosmetic() {
    override val type: CosmeticType = CosmeticType.HAT
}
