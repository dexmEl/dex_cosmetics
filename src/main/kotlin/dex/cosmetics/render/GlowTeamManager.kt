package dex.cosmetics.render

import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.types.EffectCosmetic
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.scoreboard.Team
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Formatting

/**
 * Vanilla renders GLOWING in the wearer's scoreboard team color, so we maintain one
 * `dex_glow_<formatting>` team per color and toggle membership on equip/unequip.
 *
 * Caveat: a player can only be on one team at a time. If they're already on a non-dex team (e.g.
 * PvP team from another plugin), we leave them alone instead of overriding. Treat glow cosmetics as
 * exclusive with other team-based systems.
 */
object GlowTeamManager {

    const val TEAM_PREFIX = "dex_glow_"

    /** Create (or leave existing) one team per Formatting color. Idempotent. */
    fun ensureTeamsExist(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        for (fmt in Formatting.values()) {
            if (fmt.colorValue == null) continue // skip format codes (bold, obfuscated, etc.)
            val teamName = TEAM_PREFIX + fmt.name.lowercase()
            val existing = scoreboard.getTeam(teamName)
            if (existing == null) {
                val team: Team = scoreboard.addTeam(teamName)
                team.color = fmt
                DexCosmetics.LOGGER.info("Created glow team {} ({})", teamName, fmt.name)
            } else if (existing.color != fmt) {
                existing.color = fmt
            }
        }
    }

    /** Sync the wearer's team to [glow]; null removes them from any `dex_glow_*` team. */
    fun apply(player: ServerPlayerEntity, glow: EffectCosmetic.Effect.Glow?) {
        val scoreboard = player.server.scoreboard
        val holder = player.nameForScoreboard

        val current = scoreboard.getScoreHolderTeam(holder)
        val wasOnDexTeam = current != null && current.name.startsWith(TEAM_PREFIX)
        if (current != null) {
            if (wasOnDexTeam) {
                scoreboard.removeScoreHolderFromTeam(holder, current)
            } else if (glow != null) {
                DexCosmetics.LOGGER.debug(
                    "Skipping glow team assignment for {} — already on non-dex team '{}'",
                    player.name.string,
                    current.name,
                )
                return
            }
        }

        if (glow == null) {
            // Strip GLOWING immediately so the leftover ~3s status duration doesn't outlive the
            // unequip.
            if (wasOnDexTeam) player.removeStatusEffect(StatusEffects.GLOWING)
            return
        }

        val teamName = TEAM_PREFIX + glow.outlineFormatting.name.lowercase()
        val team = scoreboard.getTeam(teamName) ?: return
        scoreboard.addScoreHolderToTeam(holder, team)
    }

    fun clear(player: ServerPlayerEntity) = apply(player, null)
}
