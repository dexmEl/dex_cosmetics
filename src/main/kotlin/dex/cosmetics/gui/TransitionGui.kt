package dex.cosmetics.gui

import dex.cosmetics.util.ServerScheduler
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Short-lived GUI that plays a frame-strip animation in the title bar by swapping the title glyph
 * each tick. Used for menu-page transitions (carousel between menu_hats / menu_items / menu_effects
 * / menu_back). Pass the per-frame glyphs in playback order; reverse playback is just
 * `frames.reversed()` from the caller.
 *
 * Frame swap mechanism: [SimpleGui.setTitle] on an already-open gui re-sends `OpenScreenS2CPacket`
 * with the same syncId, so the client just updates the title in place — no close/reopen flicker.
 *
 * Click events are ignored during playback; the player can't interact mid-transition.
 */
class TransitionGui(
    player: ServerPlayerEntity,
    private val frames: List<String>,
    private val destination: (ServerPlayerEntity) -> SimpleGui,
) : SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false) {

    private var frameIndex = 0
    private var finished = false

    init {
        title = GuiBackground.menuImage(frames[0])
    }

    override fun onOpen() {
        super.onOpen()
        scheduleNextFrame()
    }

    override fun canPlayerClose(): Boolean = false

    override fun onClick(
        index: Int,
        type: eu.pb4.sgui.api.ClickType,
        action: net.minecraft.screen.slot.SlotActionType,
        element: eu.pb4.sgui.api.elements.GuiElementInterface?,
    ): Boolean = true // swallow clicks during playback

    private fun scheduleNextFrame() {
        if (finished) return
        ServerScheduler.schedule(1) {
            if (finished || !isOpen) return@schedule
            frameIndex += 1
            if (frameIndex < frames.size) {
                setTitle(GuiBackground.menuImage(frames[frameIndex]))
                scheduleNextFrame()
            } else {
                finished = true
                val next = destination(player)
                close(true)
                next.open()
            }
        }
    }

    companion object {
        /**
         * Open a transition with [frames] (already in playback order) and open [destination] when
         * done.
         */
        fun play(
            player: ServerPlayerEntity,
            frames: List<String>,
            destination: (ServerPlayerEntity) -> SimpleGui,
        ) {
            TransitionGui(player, frames, destination).open()
        }
    }
}
