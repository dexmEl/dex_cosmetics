package dex.cosmetics.polymer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dex.cosmetics.DexCosmetics
import dex.cosmetics.config.ConfigPaths
import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.types.HatCosmetic
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder
import java.nio.file.Files
import net.minecraft.util.Identifier

/**
 * Writes one `models/{path}.json` per cosmetic and per GUI icon into the Polymer-built pack.
 * Hand-written models at `config/dex_cosmetics/model/<type>/<id>.json` are copied verbatim;
 * cosmetics without one get a synthesized `{parent, layer0}` model from their config fields.
 */
object PackModelGenerator {

    fun writeAll(builder: ResourcePackBuilder, registry: CosmeticRegistry) {
        for (cosmetic in registry.all()) writeCosmetic(builder, cosmetic)
        for (icon in GuiIconItems.allIcons()) writeIcon(builder, icon)
    }

    private fun writeCosmetic(builder: ResourcePackBuilder, cosmetic: Cosmetic) {
        val modelPath = "assets/${cosmetic.modelId.namespace}/models/${cosmetic.modelId.path}.json"
        val handWritten = ConfigPaths.handWrittenModelFor(cosmetic)
        val baseBytes =
            if (handWritten != null) {
                DexCosmetics.LOGGER.debug(
                    "Using hand-written model for ${cosmetic.id}: $handWritten"
                )
                Files.readAllBytes(handWritten)
            } else {
                buildModelJson(cosmetic.modelParent, cosmetic.textureId).toByteArray(Charsets.UTF_8)
            }
        val bytes = applyHatHeadTransform(baseBytes, cosmetic)
        val added = builder.addData(modelPath, bytes)
        if (!added) DexCosmetics.LOGGER.debug("Pack already contained {} — skipped", modelPath)
    }

    /**
     * Bake the HAT cosmetic's offset+scale into `display.head` — the only knob that moves a helmet
     * visually since vanilla locks the armor-stand passenger Y. Offsets are blocks in config,
     * pixels (1/16 block) in the model transform, so we multiply by 16. Hand-written models get
     * added to whatever the artist already set; auto-generated models start fresh.
     */
    private fun applyHatHeadTransform(bytes: ByteArray, cosmetic: Cosmetic): ByteArray {
        if (cosmetic !is HatCosmetic) return bytes
        val o = cosmetic.offset
        val s = cosmetic.scale
        if (o.x == 0f && o.y == 0f && o.z == 0f && s.x == 1f && s.y == 1f && s.z == 1f) return bytes

        val root =
            try {
                JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
            } catch (e: Exception) {
                DexCosmetics.LOGGER.warn(
                    "Could not parse model JSON for ${cosmetic.id}, leaving as-is",
                    e,
                )
                return bytes
            }
        val display =
            root.getAsJsonObject("display") ?: JsonObject().also { root.add("display", it) }
        val head = display.getAsJsonObject("head") ?: JsonObject().also { display.add("head", it) }

        val translation =
            head.getAsJsonArray("translation")
                ?: vec3(0f, 0f, 0f).also { head.add("translation", it) }
        setVec3(
            translation,
            translation[0].asFloat + o.x * 16f,
            translation[1].asFloat + o.y * 16f,
            translation[2].asFloat + o.z * 16f,
        )

        val scale = head.getAsJsonArray("scale") ?: vec3(1f, 1f, 1f).also { head.add("scale", it) }
        setVec3(scale, scale[0].asFloat * s.x, scale[1].asFloat * s.y, scale[2].asFloat * s.z)

        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun vec3(x: Float, y: Float, z: Float): JsonArray =
        JsonArray(3).apply {
            add(JsonPrimitive(x))
            add(JsonPrimitive(y))
            add(JsonPrimitive(z))
        }

    private fun setVec3(arr: JsonArray, x: Float, y: Float, z: Float) {
        arr.set(0, JsonPrimitive(x))
        arr.set(1, JsonPrimitive(y))
        arr.set(2, JsonPrimitive(z))
    }

    private fun writeIcon(builder: ResourcePackBuilder, icon: GuiIconItems.Icon) {
        val modelPath = "assets/${icon.modelId.namespace}/models/${icon.modelId.path}.json"
        val json = buildModelJson(Identifier.of("minecraft", "item/generated"), icon.textureId)
        val added = builder.addData(modelPath, json.toByteArray(Charsets.UTF_8))
        if (!added) DexCosmetics.LOGGER.debug("Pack already contained {} — skipped", modelPath)
    }

    private fun buildModelJson(parent: Identifier, texture: Identifier): String =
        """{"parent":"$parent","textures":{"layer0":"$texture"}}"""
}
