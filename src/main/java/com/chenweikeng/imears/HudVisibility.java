package com.chenweikeng.imears;

import net.minecraft.client.Minecraft;

public final class HudVisibility {
  private HudVisibility() {}

  public static boolean shouldHideScoreboard() {
    return active() && ImearsConfig.current().hideScoreboard;
  }

  public static boolean shouldHideChat() {
    return active() && ImearsConfig.current().hideChat;
  }

  public static boolean shouldHideHealth() {
    return active() && ImearsConfig.current().hideHealth;
  }

  public static boolean shouldHideHotbar() {
    return active() && ImearsConfig.current().hideHotbar;
  }

  public static boolean shouldHideExperienceLevel() {
    return active() && ImearsConfig.current().hideExperienceLevel;
  }

  public static boolean shouldHideNameTags() {
    return active() && ImearsConfig.current().hideNameTags;
  }

  public static boolean shouldHideCrosshair() {
    if (!active()) {
      return false;
    }
    HideCrosshairMode mode = ImearsConfig.current().hideCrosshairMode;
    if (mode == HideCrosshairMode.ALWAYS) {
      return true;
    }
    return mode == HideCrosshairMode.ONLY_WHEN_RIDING
        && Minecraft.getInstance().player != null
        && Minecraft.getInstance().player.isPassenger();
  }

  private static boolean active() {
    return ServerState.isImaginearsServer(Minecraft.getInstance());
  }
}
