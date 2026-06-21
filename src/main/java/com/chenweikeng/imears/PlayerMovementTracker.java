package com.chenweikeng.imears;

import net.minecraft.client.Minecraft;

/**
 * Tracks player movement and mouse clicks to suppress the not-riding alert
 * while the player is actively doing something. After the player stops moving
 * or clicking, the alert remains suppressed for {@link #MOVEMENT_SUPPRESSION_TICKS}
 * (30 seconds).
 */
public final class PlayerMovementTracker {
  private static final int MOVEMENT_SUPPRESSION_TICKS = 600; // 30 seconds @ 20 tps

  private static long lastPlayerMovementTick = -1;

  private PlayerMovementTracker() {}

  /**
   * Records the current tick if the player is pressing any movement key or
   * mouse button.
   */
  public static void track(Minecraft client, long currentTick) {
    if (client.options == null) {
      return;
    }

    boolean isMoving =
        client.options.keyUp.isDown()
            || client.options.keyDown.isDown()
            || client.options.keyLeft.isDown()
            || client.options.keyRight.isDown()
            || client.options.keyJump.isDown()
            || client.options.keyShift.isDown();

    boolean isMouseClicking =
        client.mouseHandler.isLeftPressed() || client.mouseHandler.isRightPressed();

    if (isMoving || isMouseClicking) {
      lastPlayerMovementTick = currentTick;
    }
  }

  /**
   * Returns {@code true} if the player has moved or clicked within the
   * suppression window, meaning the not-riding alert should be suppressed.
   */
  public static boolean hasPlayerMovedRecently(long currentTick) {
    if (lastPlayerMovementTick < 0) {
      return false;
    }
    return (currentTick - lastPlayerMovementTick) < MOVEMENT_SUPPRESSION_TICKS;
  }

  /** Resets tracker state (e.g. on server disconnect). */
  public static void reset() {
    lastPlayerMovementTick = -1;
  }
}
