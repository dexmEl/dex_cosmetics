package dex.cosmetics.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents

/**
 * Single source of truth for menu-feedback sounds. Centralising the SoundEvent / volume / pitch
 * here means every GUI in the mod uses identical audio feedback, and a follow-up tweak (e.g.
 * exposing a `gui_click_volume` config field, or swapping the sound for a custom one once we ship
 * our own audio assets) lives in one place.
 */
object GuiSounds {

    /**
     * Play the standard menu click sound to a single player. Volume is intentionally low so
     * stacking clicks (e.g. paging through cosmetics) don't fatigue the ear.
     */
    fun playClick(player: ServerPlayerEntity) {
        player.playSoundToPlayer(
            SoundEvents.UI_BUTTON_CLICK.value(),
            SoundCategory.PLAYERS,
            0.4f,
            1.0f,
        )
    }
}
