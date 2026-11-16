package dex.cosmetics.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dex.cosmetics.DexCosmetics
import dex.cosmetics.gui.MenuPageGui
import dex.cosmetics.permission.Permissions
import dex.cosmetics.util.Texts
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object CosmeticsCommand {
    private val COSMETIC_ID_SUGGESTIONS =
        SuggestionProvider<ServerCommandSource> {
            _: CommandContext<ServerCommandSource>,
            builder: SuggestionsBuilder ->
            for (c in DexCosmetics.registry.all()) {
                if (c.id.startsWith(builder.remainingLowerCase)) builder.suggest(c.id)
            }
            builder.buildFuture()
        }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val root = literal("cosmetics").executes(safe(::open))

        val give =
            literal("give")
                .requires(::requiresAdmin)
                .then(
                    argument("player", GameProfileArgumentType.gameProfile())
                        .then(
                            argument("cosmetic", StringArgumentType.word())
                                .suggests(COSMETIC_ID_SUGGESTIONS)
                                .executes(safe(::giveCosmetic))
                        )
                )

        val take =
            literal("take")
                .requires(::requiresAdmin)
                .then(
                    argument("player", GameProfileArgumentType.gameProfile())
                        .then(
                            argument("cosmetic", StringArgumentType.word())
                                .suggests(COSMETIC_ID_SUGGESTIONS)
                                .executes(safe(::takeCosmetic))
                        )
                )

        val list =
            literal("list")
                .requires(::requiresAdmin)
                .then(
                    argument("player", GameProfileArgumentType.gameProfile())
                        .executes(safe(::listOwned))
                )

        val reload = literal("reload").requires(::requiresAdmin).executes(safe(::reloadConfig))

        val remove = literal("remove").executes(safe(::removeSkinFromHeld))

        val generatePack =
            literal("generatepack").requires(::requiresAdmin).executes(safe(::generatePack))

        dispatcher.register(
            root.then(give).then(take).then(list).then(reload).then(remove).then(generatePack)
        )
    }

    private fun requiresAdmin(src: ServerCommandSource): Boolean =
        Permissions.check(src, DexCosmetics.config.adminPermission)

    private fun open(ctx: CommandContext<ServerCommandSource>): Int {
        val player =
            ctx.source.player
                ?: run {
                    ctx.source.sendError(Texts.mm("Only players may open the cosmetics GUI."))
                    return 0
                }
        MenuPageGui.open(player)
        return 1
    }

    private fun giveCosmetic(ctx: CommandContext<ServerCommandSource>): Int {
        val profiles = GameProfileArgumentType.getProfileArgument(ctx, "player")
        val cosmeticId = StringArgumentType.getString(ctx, "cosmetic")
        var ok = 0
        for (profile in profiles) {
            if (DexCosmetics.service.grant(profile.id, cosmeticId)) ok++
        }
        ctx.source.sendFeedback(
            { Texts.mm("<green>Granted $cosmeticId to $ok player(s).</green>") },
            true,
        )
        return ok
    }

    private fun takeCosmetic(ctx: CommandContext<ServerCommandSource>): Int {
        val profiles = GameProfileArgumentType.getProfileArgument(ctx, "player")
        val cosmeticId = StringArgumentType.getString(ctx, "cosmetic")
        var ok = 0
        for (profile in profiles) {
            if (DexCosmetics.service.revoke(profile.id, cosmeticId)) ok++
        }
        ctx.source.sendFeedback(
            { Texts.mm("<yellow>Revoked $cosmeticId from $ok player(s).</yellow>") },
            true,
        )
        return ok
    }

    private fun listOwned(ctx: CommandContext<ServerCommandSource>): Int {
        val profiles = GameProfileArgumentType.getProfileArgument(ctx, "player")
        for (profile in profiles) {
            val data = DexCosmetics.playerData.getOrLoad(profile.id)
            val owned = data.ownedSnapshot().sorted()
            ctx.source.sendFeedback(
                {
                    Texts.mm(
                        "<aqua>${profile.name} owns ${owned.size}: ${owned.joinToString(", ")}</aqua>"
                    )
                },
                false,
            )
        }
        return 1
    }

    private fun removeSkinFromHeld(ctx: CommandContext<ServerCommandSource>): Int {
        val player =
            ctx.source.player
                ?: run {
                    ctx.source.sendError(Texts.mm("Only players can run /cosmetics remove."))
                    return 0
                }
        val held = player.mainHandStack
        if (!DexCosmetics.service.removeSkin(held)) {
            player.sendMessage(Texts.mm("<gray>No skin applied to the held item.</gray>"), false)
            return 0
        }
        player.sendMessage(Texts.mm("<green>Removed skin from held item.</green>"), false)
        return 1
    }

    private fun generatePack(ctx: CommandContext<ServerCommandSource>): Int {
        check(DexCosmetics.buildResourcePack()) {
            "Resource pack generation failed; see the server log."
        }
        val mode = DexCosmetics.config.resourcePackMode.trim().lowercase()
        val tail =
            if (mode == "manual")
                " Hosted externally — connected players need to redownload from your own URL."
            else " polymer-autohost will serve it on the next connect."
        ctx.source.sendFeedback(
            {
                Texts.mm(
                    "<green>Rebuilt resource pack at <gray>polymer/resource_pack.zip</gray>.</green>$tail"
                )
            },
            true,
        )
        return 1
    }

    private fun reloadConfig(ctx: CommandContext<ServerCommandSource>): Int {
        DexCosmetics.reload(ctx.source.server)
        ctx.source.sendFeedback(
            {
                Texts.mm(
                    "<green>Reloaded settings and ${DexCosmetics.registry.size()} cosmetics; rebuilt resource pack. " +
                        "Already-connected players need to reconnect (or re-accept the pack) to see changes.</green>"
                )
            },
            true,
        )
        return 1
    }

    private fun safe(
        action: (CommandContext<ServerCommandSource>) -> Int
    ): Command<ServerCommandSource> = Command { ctx ->
        try {
            action(ctx)
        } catch (e: IllegalArgumentException) {
            ctx.source.sendError(Text.literal(e.message ?: "Invalid cosmetic configuration."))
            0
        } catch (e: Exception) {
            DexCosmetics.LOGGER.error("Cosmetics command failed", e)
            ctx.source.sendError(
                Text.literal(
                    "Cosmetics operation failed; see the server log. Changes may have partially completed for multiple players."
                )
            )
            0
        }
    }
}
