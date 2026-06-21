package com.chenweikeng.imears.mixin;

import com.chenweikeng.imears.ServerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses nearly all positional sounds while the player is riding a
 * vehicle, matching the IMF behaviour. The ride-complete sound is never
 * suppressed so the player still hears the end-of-ride chime.
 */
@Mixin(ClientLevel.class)
public class ImearsClientLevelMixin {
  private static final Identifier RIDE_COMPLETE_SOUND =
      Identifier.fromNamespaceAndPath("minecraft", "ride.complete");

  @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
  private void imears$onPlaySound(
      double d,
      double e,
      double f,
      SoundEvent soundEvent,
      SoundSource soundSource,
      float g,
      float h,
      boolean bl,
      long l,
      CallbackInfo ci) {
    Minecraft client = Minecraft.getInstance();
    if (!ServerState.isImaginearsServer(client)) {
      return;
    }

    if (soundEvent == null) {
      return;
    }

    Identifier soundId = soundEvent.location();

    // Always allow the ride-complete chime.
    if (soundId != null && soundId.equals(RIDE_COMPLETE_SOUND)) {
      return;
    }

    // Suppress all other sounds while the player is on a vehicle.
    if (client.player != null && client.player.isPassenger()) {
      ci.cancel();
    }
  }
}
