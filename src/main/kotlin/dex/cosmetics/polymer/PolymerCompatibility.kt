package dex.cosmetics.polymer

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dex.cosmetics.DexCosmetics
import dex.cosmetics.config.ConfigPaths
import eu.pb4.polymer.resourcepack.api.PolymerModelData
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/** Version-sensitive Polymer integration. Tested with Polymer 0.9.19 for Minecraft 1.21.1. */
object PolymerCompatibility {
    private val ids by lazy { ModelIds(DexCosmetics.config.customModelDataStart) }
    private val models = mutableMapOf<Pair<Item, Identifier>, PolymerModelData>()

    fun allocate(item: Item, modelId: Identifier): PolymerModelData =
        models.getOrPut(item to modelId) {
            val cmd = ids.allocate(Registries.ITEM.getId(item).toString(), modelId.toString())
            PolymerResourcePackUtils.getInstance().forceDefineModel(item, cmd, modelId, false)
        }

    /**
     * Autohost's early build can run before Fabric SERVER_STARTING and model registration.
     * Configure its documented file before server construction: auto mode lets its late startup
     * build run once; manual mode builds explicitly after registration. Other settings are
     * retained.
     */
    fun configureHosting(mode: String) {
        val directory =
            ConfigPaths.configDir.parent.resolve("polymer").also { it.createDirectories() }
        val path = directory.resolve("auto-host.json")
        val json =
            if (path.exists()) JsonParser.parseString(path.readText()).asJsonObject
            else JsonObject()
        json.addProperty("enabled", mode == "auto")
        json.addProperty("setup_early", false)
        val temporary = Files.createTempFile(directory, "dex-cosmetics-host-", ".json")
        try {
            temporary.writeText(GsonBuilder().setPrettyPrinting().create().toJson(json))
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Polymer's own asynchronous generator must finish before commands mutate model/pack state. */
    fun requireIdle() {
        check(!eu.pb4.polymer.resourcepack.impl.PolymerResourcePackMod.alreadyGeneration) {
            "Polymer is still generating a resource pack. Wait for it to finish and retry."
        }
    }

    /** Refresh autohost's hash and readiness after putting the previous valid ZIP back on disk. */
    fun packRestored() {
        PolymerResourcePackUtils.RESOURCE_PACK_FINISHED_EVENT.invoke { it.run() }
    }
}
