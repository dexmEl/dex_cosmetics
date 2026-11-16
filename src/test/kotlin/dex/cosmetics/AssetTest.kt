package dex.cosmetics

import com.google.gson.JsonParser
import dex.cosmetics.config.ConfigLoader
import dex.cosmetics.config.DefaultAssets
import dex.cosmetics.polymer.GuiIconItems
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetTest {
    @Test
    fun `every bundled file is seeded including animation metadata`() {
        val root =
            Path.of(
                System.getProperty("dex.cosmetics.projectDir"),
                "src/main/resources/default_cosmetics",
            )
        val actual =
            Files.walk(root).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .map { root.relativize(it).toString().replace('\\', '/') }
                    .toList()
                    .toSet()
            }
        val index = DefaultAssets.bundledResources()
        assertEquals(actual, index.toSet())
        assertEquals(index.size, index.distinct().size)
        assertTrue(
            index.contains("textures/effects/snowstorm_spark/gui/snowstorm_spark.png.mcmeta")
        )
        for (file in index) assertNotNull(javaClass.getResource("/default_cosmetics/$file"), file)
        assertEquals(21, ConfigLoader.readDefinitions(root.resolve("cosmetics")).size)
    }

    @Test
    fun `every registered gui icon and font bitmap has a texture`() {
        for (icon in GuiIconItems.Icon.entries) {
            assertNotNull(
                javaClass.getResource(
                    "/assets/dex_cosmetics/textures/item/${icon.textureName}.png"
                ),
                icon.name,
            )
        }
        val json =
            javaClass
                .getResourceAsStream("/assets/dex_cosmetics/font/gui.json")!!
                .bufferedReader()
                .use { JsonParser.parseReader(it).asJsonObject }
        for (provider in json.getAsJsonArray("providers")) {
            val data = provider.asJsonObject
            if (data.get("type").asString != "bitmap") continue
            val (namespace, file) = data.get("file").asString.split(":", limit = 2)
            assertNotNull(javaClass.getResource("/assets/$namespace/textures/$file"), file)
        }
    }
}
