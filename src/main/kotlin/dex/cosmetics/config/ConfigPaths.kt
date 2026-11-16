package dex.cosmetics.config

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.Cosmetic
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import net.fabricmc.loader.api.FabricLoader

object ConfigPaths {
    val configDir: Path by lazy {
        FabricLoader.getInstance().configDir.resolve(DexCosmetics.MOD_ID).also {
            it.createDirectories()
        }
    }
    val cosmeticsDir: Path
        get() = configDir.resolve("cosmetics")

    val texturesDir: Path
        get() = configDir.resolve("textures")

    val modelDir: Path
        get() = configDir.resolve("model")

    val snowstormParticlesDir: Path
        get() = configDir.resolve("snowstorm_particles")

    val guiDir: Path
        get() = configDir.resolve("gui")

    fun handWrittenModelFor(cosmetic: Cosmetic): Path? {
        val root = modelDir.resolve(cosmetic.type.dirName).normalize()
        val file = root.resolve("${cosmetic.id}.json").normalize()
        require(file.startsWith(root)) { "Model path leaves its category directory" }
        return file.takeIf { it.isRegularFile() }
    }
}
