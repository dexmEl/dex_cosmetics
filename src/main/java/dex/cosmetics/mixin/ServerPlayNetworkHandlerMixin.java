package dex.cosmetics.mixin;

import dex.cosmetics.render.HatEquipmentBroadcaster;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-emit the cosmetic packet immediately after vanilla processes a player-inventory slot click —
 * vanilla resets the helmet slot to its real (usually empty) value and we'd see a flicker
 * otherwise. Cursor reset is gated to slot 5 AND a server-side empty cursor so a legitimate
 * iron-helmet pickup doesn't blank the cursor.
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

  private static final int HELMET_SLOT = 5;

  @Shadow public ServerPlayerEntity player;

  @Inject(method = "onClickSlot", at = @At("TAIL"))
  private void dexCosmetics$refreshCosmeticAfterClick(ClickSlotC2SPacket packet, CallbackInfo ci) {
    if (!(player.currentScreenHandler instanceof PlayerScreenHandler)) return;
    HatEquipmentBroadcaster.refreshFor(player);
    if (packet.getSlot() == HELMET_SLOT && player.currentScreenHandler.getCursorStack().isEmpty()) {
      HatEquipmentBroadcaster.clearCursorFor(player);
    }
  }
}
