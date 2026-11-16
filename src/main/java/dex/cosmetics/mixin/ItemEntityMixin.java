package dex.cosmetics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dex.cosmetics.DexCosmetics;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies sharing rules at inventory insertion, after vanilla's pickup eligibility checks. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

  @WrapOperation(
      method = "onPlayerCollision",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/player/PlayerInventory;insertStack(Lnet/minecraft/item/ItemStack;)Z"))
  private boolean dexCosmetics$insertPickedUpStack(
      PlayerInventory inventory,
      ItemStack stack,
      Operation<Boolean> original,
      PlayerEntity player) {
    if (!(player instanceof ServerPlayerEntity serverPlayer)) {
      return original.call(inventory, stack);
    }
    return DexCosmetics.INSTANCE
        .getService()
        .insertPickedUpStack(
            serverPlayer.getUuid(),
            stack,
            DexCosmetics.INSTANCE.getConfig().getAllowItemSkinSharing(),
            candidate -> original.call(inventory, candidate));
  }
}
