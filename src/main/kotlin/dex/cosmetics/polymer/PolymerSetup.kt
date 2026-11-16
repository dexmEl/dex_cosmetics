package dex.cosmetics.polymer

import dex.cosmetics.DexCosmetics
import dex.cosmetics.config.ConfigPaths
import eu.pb4.polymer.core.api.item.PolymerItemUtils
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder
import java.nio.file.Files
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent

object PolymerSetup {
    fun register() {
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register { builder ->
            pipeUserTextures(builder)
            pipeSnowstormParticles(builder)
            PackModelGenerator.writeAll(builder, DexCosmetics.registry)
            GlowIconGenerator.writeAll(builder, DexCosmetics.registry)
            MenuStripGenerator.writeAll(builder)
            writeBlocksAtlas(builder)
            writeParticlesAtlas(builder)
        }

        // Flag vanilla stacks carrying AppliedSkinComponent as polymer items so
        // ITEM_MODIFICATION_EVENT
        // fires for them. Without this, plain vanilla items skip the modification chain entirely.
        PolymerItemUtils.ITEM_CHECK.register { stack -> AppliedSkinComponent.read(stack) != null }

        PolymerItemUtils.ITEM_MODIFICATION_EVENT.register { original, client, _ ->
            val skinId = AppliedSkinComponent.read(original) ?: return@register client
            val cmd = CosmeticItems.skinCmdFor(skinId, original.item)
            if (cmd == null) {
                DexCosmetics.LOGGER.warn(
                    "Skin '{}' applied but no CMD registered for item {} (incompatible or unregistered).",
                    skinId,
                    net.minecraft.registry.Registries.ITEM.getId(original.item),
                )
                return@register client
            }
            client.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(cmd))
            if (DexCosmetics.config.renameItemToSkinName) {
                DexCosmetics.registry.get(skinId)?.let { cosmetic ->
                    client.set(
                        DataComponentTypes.CUSTOM_NAME,
                        dex.cosmetics.util.Texts.mm(cosmetic.displayName),
                    )
                }
            }
            client
        }
    }

    /**
     * Stitch the cosmetic-type texture directories onto the vanilla blocks atlas. Without this,
     * 1.21+ won't include them and item models that reference our PNGs render purple/black. Source
     * dirs must match [dex.cosmetics.cosmetic.CosmeticType.dirName]. The two chainmail singles
     * re-include Polymer's own atlas entries (this file overwrites Polymer's blocks.json).
     */
    private fun writeBlocksAtlas(builder: ResourcePackBuilder) {
        val json =
            """{
			"sources": [
				{"type": "directory", "source": "hats", "prefix": "hats/"},
				{"type": "directory", "source": "back", "prefix": "back/"},
				{"type": "directory", "source": "items", "prefix": "items/"},
				{"type": "directory", "source": "effects", "prefix": "effects/"},
				{"type": "single", "sprite": "minecraft:trims/models/armor/chainmail", "resource": "minecraft:trims/models/armor/chainmail"},
				{"type": "single", "sprite": "minecraft:trims/models/armor/chainmail_leggings", "resource": "minecraft:trims/models/armor/chainmail_leggings"}
			]
		}"""
        builder.addData("assets/minecraft/atlases/blocks.json", json.toByteArray(Charsets.UTF_8))
    }

    /**
     * Register effect PNGs on the particles atlas — Cobblemon's Bedrock particle reader resolves
     * `basic_render_parameters.texture` against this atlas, not blocks/items. Using `effects/` (not
     * `particles/`) sidesteps Cobblemon's `SnowstormParticleReader` substring-stripping.
     */
    private fun writeParticlesAtlas(builder: ResourcePackBuilder) {
        val json =
            """{
			"sources": [
				{"type": "directory", "source": "effects", "prefix": "effects/"}
			]
		}"""
        builder.addData("assets/minecraft/atlases/particles.json", json.toByteArray(Charsets.UTF_8))
    }

    /**
     * Copy snowstorm `.particle.json` files into the pack under `assets/<mod>/bedrock/particles/` —
     * Cobblemon's repository scans there.
     */
    private fun pipeSnowstormParticles(builder: ResourcePackBuilder) {
        val root = ConfigPaths.snowstormParticlesDir
        if (!Files.exists(root)) return
        Files.walk(root).use { stream ->
            stream
                .filter {
                    Files.isRegularFile(it) && it.fileName.toString().endsWith(".particle.json")
                }
                .forEach { path ->
                    val relative = root.relativize(path).toString().replace('\\', '/')
                    val packPath = "assets/${DexCosmetics.MOD_ID}/bedrock/particles/$relative"
                    builder.addData(packPath, Files.readAllBytes(path))
                }
        }
    }

    /**
     * Direct disk-to-pack copy of `config/dex_cosmetics/textures/` → `assets/<mod>/textures/`.
     * Blockbench can reference `dex_cosmetics:<rest>` matching the disk path. Avoid placing
     * cosmetic textures under `textures/item/` or `textures/gui/` (mod's own GUI icons live there).
     */
    private fun pipeUserTextures(builder: ResourcePackBuilder) {
        val texturesRoot = ConfigPaths.texturesDir
        if (!Files.exists(texturesRoot)) return
        Files.walk(texturesRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { path ->
                    val relative = texturesRoot.relativize(path).toString().replace('\\', '/')
                    val packPath = "assets/${DexCosmetics.MOD_ID}/textures/$relative"
                    builder.addData(packPath, Files.readAllBytes(path))
                }
        }
    }
}
