package dex.cosmetics

import dex.cosmetics.command.CosmeticsCommand
import dex.cosmetics.config.ConfigLoader
import dex.cosmetics.config.ConfigurationReloader
import dex.cosmetics.config.CosmeticsConfig
import dex.cosmetics.config.DefaultAssets
import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.CosmeticService
import dex.cosmetics.data.PlayerDataManager
import dex.cosmetics.data.SqlitePlayerDataStorage
import dex.cosmetics.event.PlayerEvents
import dex.cosmetics.polymer.CosmeticItems
import dex.cosmetics.polymer.GuiIconItems
import dex.cosmetics.polymer.PolymerCompatibility
import dex.cosmetics.polymer.PolymerSetup
import dex.cosmetics.render.CosmeticTicker
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DexCosmetics : ModInitializer {
    const val MOD_ID = "dex_cosmetics"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: CosmeticsConfig
        private set

    lateinit var registry: CosmeticRegistry
        private set

    lateinit var playerData: PlayerDataManager
        private set

    lateinit var ticker: CosmeticTicker
        private set

    lateinit var service: CosmeticService
        private set

    fun id(path: String): Identifier = Identifier.of(MOD_ID, path)

    /** A failed build preserves the last pack on disk and reports failure to callers. */
    fun buildResourcePack(): Boolean {
        PolymerCompatibility.requireIdle()
        val path = PolymerResourcePackUtils.getMainPath()
        val backup =
            if (Files.isRegularFile(path))
                Files.createTempFile(path.parent, "dex-cosmetics-pack-", ".zip")
            else null
        var backedUp = false
        try {
            if (backup != null) {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
                backedUp = true
            }
            val ok = PolymerResourcePackUtils.buildMain()
            if (!ok && backedUp) {
                Files.copy(backup!!, path, StandardCopyOption.REPLACE_EXISTING)
                PolymerCompatibility.packRestored()
            }
            LOGGER.info("Polymer pack rebuild: {}", if (ok) "OK" else "FAILED")
            return ok
        } catch (e: Exception) {
            if (backedUp) {
                try {
                    Files.copy(backup!!, path, StandardCopyOption.REPLACE_EXISTING)
                    PolymerCompatibility.packRestored()
                } catch (restore: Exception) {
                    e.addSuppressed(restore)
                }
            }
            LOGGER.error("Polymer pack rebuild failed", e)
            return false
        } finally {
            if (backup != null) {
                try {
                    Files.deleteIfExists(backup)
                } catch (e: Exception) {
                    LOGGER.warn("Could not remove pack backup {}", backup, e)
                }
            }
        }
    }

    fun reload(server: MinecraftServer) {
        PolymerCompatibility.requireIdle()
        val candidate = ConfigLoader.loadOrCreateGlobalConfig()
        candidate.requireReloadCompatible(config)
        val cosmetics = ConfigLoader.loadAllCosmetics()
        ConfigurationReloader(
                registry,
                { config },
                { config = it },
                {
                    CosmeticItems.registerAll(registry)
                    GuiIconItems.register()
                },
                ::buildResourcePack,
            )
            .reload(candidate, cosmetics)
        var refreshFailure: Exception? = null
        for (player in server.playerManager.playerList) {
            try {
                service.reconcile(player.uuid)
            } catch (e: Exception) {
                LOGGER.error("Could not reconcile cosmetics for {} after reload", player.uuid, e)
                if (refreshFailure == null) refreshFailure = e else refreshFailure.addSuppressed(e)
            }
        }
        check(refreshFailure == null) {
            "Configuration reloaded, but some player equipment could not be saved. See the server log."
        }
    }

    override fun onInitialize() {
        LOGGER.info("Initializing Dex Cosmetics")

        config = ConfigLoader.loadOrCreateGlobalConfig()
        PolymerCompatibility.configureHosting(config.resourcePackMode)
        registry = CosmeticRegistry()
        ticker = CosmeticTicker(registry)

        PolymerSetup.register()
        ConfigLoader.ensureCategoryDirsExist()
        DefaultAssets.copyCosmeticsIfMissing()
        DefaultAssets.copyGuiIfMissing()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val storage =
                SqlitePlayerDataStorage(server.getSavePath(WorldSavePath.ROOT).resolve(MOD_ID))
            storage.open()
            playerData = PlayerDataManager(storage)
            service =
                CosmeticService(registry, playerData) { uuid ->
                    server.playerManager.getPlayer(uuid)?.let(ticker::syncFromEquipped)
                }
            try {
                registry.replaceAll(ConfigLoader.loadAllCosmetics())
                // After tag load — needed for tag-based item-skin compatibility.
                CosmeticItems.registerAll(registry)
                GuiIconItems.register()
                // Auto mode lets autohost build once after registration; manual mode builds here.
                if (config.resourcePackMode == "manual")
                    check(buildResourcePack()) { "Initial resource pack generation failed" }
            } catch (e: Exception) {
                playerData.shutdown()
                throw e
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            // Scoreboard.addTeam broadcasts a packet, so PlayerManager must already exist.
            dex.cosmetics.render.GlowTeamManager.ensureTeamsExist(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            for (player in server.playerManager.playerList) ticker.onPlayerQuit(player)
            dex.cosmetics.util.ServerScheduler.clear()
            if (::playerData.isInitialized) playerData.shutdown()
        }

        PlayerEvents.register()
        ticker.register()
        dex.cosmetics.util.ServerScheduler.register()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CosmeticsCommand.register(dispatcher)
        }

        LOGGER.info("Dex Cosmetics initialized")
    }
}
