package dex.cosmetics.polymer

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.types.EffectCosmetic
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Tints the bundled grayscale `glow_icon` PNG by each glow cosmetic's `colorRgb` and writes one PNG
 * per cosmetic into the pack. The source `.mcmeta` (animation frames) is copied alongside so all
 * tinted variants share the same animation cadence.
 */
object GlowIconGenerator {

    private const val BASE_PNG =
        "/assets/${DexCosmetics.MOD_ID}/textures/item/glow_icon/glow_icon.png"
    private const val BASE_MCMETA = "$BASE_PNG.mcmeta"

    fun writeAll(builder: ResourcePackBuilder, registry: CosmeticRegistry) {
        val cls = GlowIconGenerator::class.java
        val basePng =
            cls.getResourceAsStream(BASE_PNG)?.use { it.readAllBytes() }
                ?: run {
                    DexCosmetics.LOGGER.warn(
                        "glow_icon base PNG missing at $BASE_PNG — no tinted glow icons will be generated"
                    )
                    return
                }
        val baseMcmeta = cls.getResourceAsStream(BASE_MCMETA)?.use { it.readAllBytes() }

        val sourceImage = ImageIO.read(ByteArrayInputStream(basePng))

        for (cosmetic in registry.all()) {
            if (cosmetic !is EffectCosmetic) continue
            val glow = cosmetic.effect as? EffectCosmetic.Effect.Glow ?: continue

            val tinted = tintGrayscale(sourceImage, glow.colorRgb)
            val pngBytes =
                ByteArrayOutputStream().use { baos ->
                    ImageIO.write(tinted, "png", baos)
                    baos.toByteArray()
                }

            val packPath =
                "assets/${cosmetic.textureId.namespace}/textures/${cosmetic.textureId.path}.png"
            builder.addData(packPath, pngBytes)
            if (baseMcmeta != null) {
                builder.addData("$packPath.mcmeta", baseMcmeta)
            }
        }
    }

    /**
     * Multiply each pixel's grayscale (red channel) value by the 0xRRGGBB tint; alpha unchanged.
     */
    private fun tintGrayscale(source: BufferedImage, tintRgb: Int): BufferedImage {
        val tintR = (tintRgb shr 16) and 0xFF
        val tintG = (tintRgb shr 8) and 0xFF
        val tintB = tintRgb and 0xFF

        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val gray = (argb shr 16) and 0xFF
                val r = (gray * tintR) / 255
                val g = (gray * tintG) / 255
                val b = (gray * tintB) / 255
                out.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return out
    }
}
