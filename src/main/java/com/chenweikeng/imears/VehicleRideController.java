package com.chenweikeng.imears;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class VehicleRideController {
  private static final int NOT_RIDING_ALERT_INTERVAL_TICKS = 200;
  private static final int WINDOW_RESTORE_GRACE_TICKS = 10;
  private static final Identifier NOT_RIDING_SOUND =
      Identifier.fromNamespaceAndPath("minecraft", "entity.experience_orb.pickup");

  private static boolean cursorReleasedByMod = false;
  private static boolean wasOnVehicle = false;
  private static int windowRestoreGrace = 0;
  private static long tickCounter = 0;
  private static long lastAlertTick = -NOT_RIDING_ALERT_INTERVAL_TICKS;

  private VehicleRideController() {}

  public static void tick(Minecraft client) {
    tickCounter++;

    if (!ServerState.isImaginearsServer(client) || client.player == null || client.level == null) {
      resetTransientState(client);
      return;
    }

    if (windowRestoreGrace > 0) {
      windowRestoreGrace--;
    }

    boolean onVehicle = client.player.isPassenger();

    // Track movement for not-riding alert cooldown.
    PlayerMovementTracker.track(client, tickCounter);

    tickCursorRelease(client, onVehicle);
    tickWindowMinimize(client, onVehicle);
    tickNotRidingAlert(client, onVehicle);
    RideProfiler.tick(client, onVehicle);
    RideProgressTracker.tick(client, onVehicle);

    wasOnVehicle = onVehicle;
  }

  public static boolean isCursorReleasedByMod() {
    return cursorReleasedByMod;
  }

  public static boolean isWithinWindowRestoreGrace() {
    return windowRestoreGrace > 0;
  }

  // ---------------------------------------------------------------------------
  // Cursor release / grab
  // ---------------------------------------------------------------------------

  /**
   * On ride start, release the mouse exactly once (not every tick). The player
   * can left-click to re-grab (vanilla Minecraft behaviour) and right-click to
   * release again while still on the ride. On ride end the mouse is grabbed
   * back.
   */
  private static void tickCursorRelease(Minecraft client, boolean onVehicle) {
    // Release mouse once on ride start.
    if (!wasOnVehicle && onVehicle && client.screen == null) {
      client.mouseHandler.releaseMouse();
      cursorReleasedByMod = true;
    }

    // Grab mouse back on ride end.
    if (wasOnVehicle && !onVehicle && cursorReleasedByMod) {
      cursorReleasedByMod = false;
      if (client.screen == null) {
        client.mouseHandler.grabMouse();
      }
    }

    // While on a vehicle, right-click re-releases the mouse so the player can
    // toggle between grabbed (left-click) and released (right-click).
    if (onVehicle && client.mouseHandler.isRightPressed() && client.screen == null) {
      client.mouseHandler.releaseMouse();
    }
  }

  // ---------------------------------------------------------------------------
  // Window minimise / restore
  // ---------------------------------------------------------------------------

  /** Minimise the window when the player mounts a vehicle, restore on dismount. */
  private static void tickWindowMinimize(Minecraft client, boolean onVehicle) {
    if (!wasOnVehicle && onVehicle) {
      WindowMinimizeHandler.minimizeWindow();
    }

    if (wasOnVehicle && !onVehicle) {
      windowRestoreGrace = WINDOW_RESTORE_GRACE_TICKS;
      WindowMinimizeHandler.restoreWindow();
      WindowMinimizeHandler.requestAttention();
    }
  }

  // ---------------------------------------------------------------------------
  // Not-riding alert
  // ---------------------------------------------------------------------------

  /**
   * Plays a sound every {@link #NOT_RIDING_ALERT_INTERVAL_TICKS} ticks when the
   * player is not on a vehicle. The alert is suppressed while the player is
   * actively moving or clicking (with a 30-second cooldown after the last
   * movement), matching the IMF behaviour.
   */
  private static void tickNotRidingAlert(Minecraft client, boolean onVehicle) {
    if (onVehicle) {
      return;
    }

    if (tickCounter - lastAlertTick < NOT_RIDING_ALERT_INTERVAL_TICKS) {
      return;
    }

    // Suppress alert when the player has moved or clicked recently.
    if (PlayerMovementTracker.hasPlayerMovedRecently(tickCounter)) {
      return;
    }

    client.level.playSound(
        client.player,
        client.player.getX(),
        client.player.getY(),
        client.player.getZ(),
        SoundEvent.createVariableRangeEvent(NOT_RIDING_SOUND),
        SoundSource.MASTER,
        1.0f,
        1.0f);
    lastAlertTick = tickCounter;
  }

  // ---------------------------------------------------------------------------
  // Reset
  // ---------------------------------------------------------------------------

  private static void resetTransientState(Minecraft client) {
    if (cursorReleasedByMod) {
      cursorReleasedByMod = false;
      if (client != null && client.player != null && client.screen == null) {
        client.mouseHandler.grabMouse();
      }
    }
    wasOnVehicle = false;
    windowRestoreGrace = 0;
    tickCounter = 0;
    lastAlertTick = -NOT_RIDING_ALERT_INTERVAL_TICKS;
    PlayerMovementTracker.reset();
    RideProfiler.reset(client, "reset_transient_state");
    RideProgressTracker.tick(client, false);
  }
}
