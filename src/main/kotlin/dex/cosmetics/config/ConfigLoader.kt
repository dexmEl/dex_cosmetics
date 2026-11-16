package dex.cosmetics.config

import com.google.gson.JsonParser
import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Reads candidates without mutating live state. Any invalid file rejects the complete reload. */
object ConfigLoader {
    fun loadOrCreateGlobalConfig(
        path: Path = ConfigPaths.configDir.resolve("config.json")
    ): CosmeticsConfig {
        if (!path.exists()) {
            val defaults = CosmeticsConfig()
            path.writeText(ConfigJson.gson.toJson(defaults))
            return defaults
        }
        return try {
            val json = ConfigJson.parse(path.readText())
            require(json.isJsonObject) { "Expected a JSON object" }
            val removed =
                setOf(
                    "require_resource_pack",
                    "resource_pack_kick_message",
                    "autosave_interval_seconds",
                )
            val obsolete = json.asJsonObject.keySet().intersect(removed)
            if (obsolete.isNotEmpty())
                DexCosmetics.LOGGER.warn(
                    "Ignored obsolete settings in {}: {}. See README migration notes.",
                    path,
                    obsolete,
                )
            ConfigJson.gson.fromJson(json, CosmeticsConfig::class.java).validated()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid $path: ${e.message}", e)
        }
    }

    fun ensureCategoryDirsExist() {
        for (type in CosmeticType.entries) {
            ConfigPaths.cosmeticsDir.resolve(type.dirName).createDirectories()
            ConfigPaths.texturesDir.resolve(type.dirName).createDirectories()
            ConfigPaths.modelDir.resolve(type.dirName).createDirectories()
        }
        ConfigPaths.snowstormParticlesDir.createDirectories()
        ConfigPaths.guiDir.createDirectories()
    }

    fun loadAllCosmetics(root: Path = ConfigPaths.cosmeticsDir): List<Cosmetic> =
        readDefinitions(root).map { (file, def) ->
            try {
                CosmeticFactory.build(def).also(::validateModel)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid $file: ${e.message}", e)
            }
        }

    internal fun readDefinitions(root: Path): List<Pair<Path, CosmeticDefinition>> {
        val files =
            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && it.extension in setOf("json", "json5") }
                    .sorted()
                    .toList()
            }
        val ids = mutableMapOf<String, Path>()
        return files.map { file ->
            try {
                val json = ConfigJson.parse(file.readText())
                require(json.isJsonObject) { "Expected a JSON object" }
                val def = ConfigJson.gson.fromJson(json, CosmeticDefinition::class.java)
                DefinitionValidator.validate(def)
                val previous = ids.putIfAbsent(def.id, file)
                require(previous == null) { "Duplicate id '${def.id}', also defined in $previous" }
                file to def
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid $file: ${e.message}", e)
            }
        }
    }

    private fun validateModel(cosmetic: Cosmetic) {
        val file = ConfigPaths.handWrittenModelFor(cosmetic) ?: return
        val root = JsonParser.parseString(file.readText()).asJsonObject
        root.getAsJsonObject("display")?.entrySet()?.forEach { (perspective, value) ->
            val transform = value.asJsonObject
            for (key in listOf("translation", "scale", "rotation")) {
                val vector = transform.get(key) ?: continue
                require(vector.isJsonArray && vector.asJsonArray.size() == 3) {
                    "Model $file display.$perspective.$key must have three numbers"
                }
                require(vector.asJsonArray.all { it.asDouble.isFinite() }) {
                    "Model $file has a non-finite transform"
                }
            }
        }
    }
}
