package dex.cosmetics.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.google.gson.JsonElement
import com.google.gson.JsonParser

internal object ConfigJson {
    /**
     * GSON defaults to allocating via Unsafe, which skips constructors — so Kotlin data class
     * defaults never fire for missing JSON fields. InstanceCreators force GSON to call the actual
     * constructor; GSON then overrides only fields present in JSON.
     */
    val gson: Gson =
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(
                CosmeticDefinition::class.java,
                InstanceCreator<CosmeticDefinition> {
                    CosmeticDefinition(
                        id = "",
                        type = "",
                        displayName = "",
                        model = "",
                        texture = "",
                    )
                },
            )
            .registerTypeAdapter(
                CosmeticDefinition.TransformBlock::class.java,
                InstanceCreator { CosmeticDefinition.TransformBlock() },
            )
            .registerTypeAdapter(
                CosmeticDefinition.ItemSkinBlock::class.java,
                InstanceCreator { CosmeticDefinition.ItemSkinBlock() },
            )
            .registerTypeAdapter(
                CosmeticDefinition.EffectBlock::class.java,
                InstanceCreator { CosmeticDefinition.EffectBlock() },
            )
            .registerTypeAdapter(
                CosmeticDefinition.ParticleBlock::class.java,
                InstanceCreator { CosmeticDefinition.ParticleBlock() },
            )
            .registerTypeAdapter(
                CosmeticDefinition.GlowBlock::class.java,
                InstanceCreator { CosmeticDefinition.GlowBlock() },
            )
            .registerTypeAdapter(
                CosmeticDefinition.SnowstormBlock::class.java,
                InstanceCreator { CosmeticDefinition.SnowstormBlock() },
            )
            .registerTypeAdapter(CosmeticsConfig::class.java, InstanceCreator { CosmeticsConfig() })
            .create()

    /** Explicit nulls bypass Kotlin defaults; reject them with a useful field path. */
    fun parse(text: String): JsonElement =
        JsonParser.parseString(text).also { rejectNulls(it, "root") }

    private fun rejectNulls(value: JsonElement, path: String) {
        require(!value.isJsonNull) { "$path must not be null" }
        if (value.isJsonObject)
            value.asJsonObject.entrySet().forEach { (key, child) ->
                rejectNulls(child, "$path.$key")
            }
        if (value.isJsonArray)
            value.asJsonArray.forEachIndexed { index, child -> rejectNulls(child, "$path[$index]") }
    }
}
