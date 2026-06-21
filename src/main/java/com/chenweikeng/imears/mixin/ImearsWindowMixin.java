package com.chenweikeng.imears.mixin;

import com.chenweikeng.imears.VehicleRideController;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class ImearsWindowMixin {
  @Inject(method = "isFocused", at = @At("HEAD"), cancellable = true)
  private void imears$keepFocusedWhenCursorReleased(CallbackInfoReturnable<Boolean> cir) {
    if (VehicleRideController.isCursorReleasedByMod()
        || VehicleRideController.isWithinWindowRestoreGrace()) {
      cir.setReturnValue(true);
    }
  }
}
