package com.chenweikeng.imears.mixin;

import com.chenweikeng.imears.HudVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class ImearsEntityRendererMixin {
  @Inject(
      method =
          "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void imears$hideNameTags(
      EntityRenderState entityRenderState,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      CameraRenderState cameraRenderState,
      CallbackInfo ci) {
    if (HudVisibility.shouldHideNameTags()) {
      ci.cancel();
    }
  }
}
