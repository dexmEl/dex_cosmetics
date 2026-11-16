package dex.cosmetics

import dex.cosmetics.config.ConfigLoader
import dex.cosmetics.config.ConfigurationReloader
import dex.cosmetics.config.CosmeticsConfig
import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.types.HatCosmetic
import java.nio.file.Path
import kotlin.io.path.writeText
import net.minecraft.util.Identifier
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConfigurationTest {
    @TempDir lateinit var directory: Path
    private val valid =
        """{"id":"hat","type":"HAT","display_name":"Hat","model":"test:hat","texture":"test:hat"}"""

    private fun hat(id: String) =
        HatCosmetic(
            id,
            id,
            emptyList(),
            Identifier.of("test:$id"),
            Identifier.of("test:$id"),
            Identifier.of("minecraft:item/generated"),
            Vector3f(),
            Vector3f(1f),
        )

    @Test
    fun `omitted optional fields use Kotlin defaults`() {
        directory.resolve("hat.json").writeText(valid)
        val definition = ConfigLoader.readDefinitions(directory).single().second
        assertEquals(1f, definition.transform.scaleX)
        assertEquals(4, definition.effect.particle.intervalTicks)
        assertTrue(definition.itemSkin.compatibleItems.isEmpty())
    }

    @Test
    fun `duplicate ids and explicit null fields are rejected with filenames`() {
        directory.resolve("first.json").writeText(valid)
        directory.resolve("second.json").writeText(valid)
        val duplicate =
            assertThrows(IllegalArgumentException::class.java) {
                ConfigLoader.readDefinitions(directory)
            }
        assertTrue(duplicate.message!!.contains("Duplicate id"))
        directory
            .resolve("second.json")
            .writeText(valid.replace("\"hat\",\"type\"", "null,\"type\""))
        val explicitNull =
            assertThrows(IllegalArgumentException::class.java) {
                ConfigLoader.readDefinitions(directory)
            }
        assertTrue(explicitNull.message!!.contains("second.json"))
        assertTrue(explicitNull.message!!.contains("root.id"))
    }

    @Test
    fun `malformed definitions reject a candidate without clearing the registry`() {
        val registry = CosmeticRegistry()
        val hat = hat("old")
        registry.replaceAll(listOf(hat))
        directory.resolve("bad.json").writeText(valid.replace("\"hat\"", "\"../hat\""))
        assertThrows(IllegalArgumentException::class.java) {
            ConfigLoader.readDefinitions(directory)
        }
        assertEquals(hat, registry.get("old"))
    }

    @Test
    fun `failed pack generation restores global config and cosmetic registry`() {
        val previous = hat("old")
        val candidate = hat("new")
        val registry = CosmeticRegistry().apply { replaceAll(listOf(previous)) }
        var config = CosmeticsConfig()
        val registrations = mutableListOf<List<String>>()
        val reloader =
            ConfigurationReloader(
                registry,
                { config },
                { config = it },
                { registrations.add(registry.all().map { it.id }) },
                {
                    assertTrue(config.renameItemToSkinName)
                    assertEquals(candidate, registry.get(candidate.id))
                    false
                },
            )
        assertThrows(IllegalStateException::class.java) {
            reloader.reload(config.copy(renameItemToSkinName = true), listOf(candidate))
        }
        assertFalse(config.renameItemToSkinName)
        assertEquals(listOf(listOf("new"), listOf("old")), registrations)
        assertEquals(listOf(previous), registry.all().toList())
    }

    @Test
    fun `successful reload replaces global settings and definitions together`() {
        val registry = CosmeticRegistry().apply { replaceAll(listOf(hat("old"))) }
        var config = CosmeticsConfig()
        val updated = config.copy(renameItemToSkinName = true, particleTickBudgetPerPlayer = 1)
        val candidate = listOf(hat("new"))
        ConfigurationReloader(
                registry,
                { config },
                { config = it },
                {},
                {
                    assertEquals(updated, config)
                    assertEquals(candidate, registry.all().toList())
                    true
                },
            )
            .reload(updated, candidate)
        assertEquals(updated, config)
        assertEquals(candidate, registry.all().toList())
    }

    @Test
    fun `restart-only changes are rejected before registering models or building packs`() {
        val config = CosmeticsConfig()
        val reloader =
            ConfigurationReloader(
                CosmeticRegistry(),
                { config },
                { throw AssertionError("must not mutate") },
                { throw AssertionError("must not register") },
                { throw AssertionError("must not build") },
            )
        assertThrows(IllegalArgumentException::class.java) {
            reloader.reload(config.copy(customModelDataStart = 5000), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            reloader.reload(config.copy(resourcePackMode = "manual"), emptyList())
        }
    }

    @Test
    fun `nonfinite and excessive particle settings are rejected`() {
        directory
            .resolve("effect.json")
            .writeText(
                """{"id":"effect","type":"EFFECT","display_name":"Effect","model":"test:effect","texture":"test:effect","effect":{"kind":"particle","particle":{"style":"halo","count":999999}}}"""
            )
        assertThrows(IllegalArgumentException::class.java) {
            ConfigLoader.readDefinitions(directory)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CosmeticsConfig(particleTickBudgetPerPlayer = -1).validated()
        }
    }
}
