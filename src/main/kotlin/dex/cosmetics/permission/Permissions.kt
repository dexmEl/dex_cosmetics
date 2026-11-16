package dex.cosmetics.permission

import dex.cosmetics.DexCosmetics
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity

/** LuckPerms-aware permission checks. Falls back to vanilla OP level 2 if LuckPerms is absent. */
object Permissions {
    private val luckPermsLoaded: Boolean by lazy {
        FabricLoader.getInstance().isModLoaded("luckperms")
    }

    fun check(source: ServerCommandSource, node: String, opFallbackLevel: Int = 2): Boolean {
        val player = source.player ?: return source.hasPermissionLevel(opFallbackLevel)
        return check(player, node, opFallbackLevel)
    }

    fun check(player: ServerPlayerEntity, node: String, opFallbackLevel: Int = 2): Boolean {
        if (luckPermsLoaded) {
            try {
                val api = net.luckperms.api.LuckPermsProvider.get()
                val user =
                    api.userManager.getUser(player.uuid)
                        ?: return player.hasPermissionLevel(opFallbackLevel)
                val data = user.cachedData.permissionData
                return data.checkPermission(node).asBoolean()
            } catch (e: Exception) {
                DexCosmetics.LOGGER.debug("LuckPerms check failed, falling back to op level", e)
            }
        }
        return player.hasPermissionLevel(opFallbackLevel)
    }
}
