package com.chenweikeng.imears.mixin;

import com.chenweikeng.imears.HudVisibility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class ImearsGuiMixin {
  @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
  private void imears$hideScoreboard(
      GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (HudVisibility.shouldHideScoreboard()) {
      ci.cancel();
    }
  }

  @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
  private void imears$hideChat(
      GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (HudVisibility.shouldHideChat()) {
      ci.cancel();
    }
  }

  @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
  private void imears$hidePlayerHealth(GuiGraphicsExtractor extractor, CallbackInfo ci) {
    if (HudVisibility.shouldHideHealth()) {
      ci.cancel();
    }
  }

  @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
  private void imears$hideVehicleHealth(GuiGraphicsExtractor extractor, CallbackInfo ci) {
    if (HudVisibility.shouldHideHealth()) {
      ci.cancel();
    }
  }

  @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
  private void imears$hideHotbar(
      GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (HudVisibility.shouldHideHotbar()) {
      ci.cancel();
    }
  }

  @Redirect(
      method = "extractHotbarAndDecorations",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
  private void imears$hideExperienceLevel(
      GuiGraphicsExtractor extractor, Font font, int level) {
    if (!HudVisibility.shouldHideExperienceLevel()) {
      ContextualBarRenderer.extractExperienceLevel(extractor, font, level);
    }
  }

  @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
  private void imears$hideCrosshair(
      GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (HudVisibility.shouldHideCrosshair()) {
      ci.cancel();
    }
  }
}
