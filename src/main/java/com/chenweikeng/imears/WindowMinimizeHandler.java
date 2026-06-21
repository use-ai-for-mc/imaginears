package com.chenweikeng.imears;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimizes and restores the Minecraft window via GLFW when a ride starts
 * and ends. Window geometry (size, position, maximized state) is saved before
 * minimizing so it can be accurately restored.
 */
public final class WindowMinimizeHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger("imears/window");

  private static boolean hasSavedState = false;
  private static int savedW = -1;
  private static int savedH = -1;
  private static int savedX = Integer.MIN_VALUE;
  private static int savedY = Integer.MIN_VALUE;
  private static boolean savedMaximized = false;

  private WindowMinimizeHandler() {}

  /**
   * Saves the current window geometry and minimises (iconifies) the window.
   * Safe to call when the window is already minimised — it will be a no-op.
   */
  public static void minimizeWindow() {
    Minecraft client = Minecraft.getInstance();
    if (client.getWindow() == null) {
      return;
    }

    long handle = client.getWindow().handle();
    boolean isMinimized =
        GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;

    if (!isMinimized) {
      int[] sw = new int[1], sh = new int[1];
      int[] px = new int[1], py = new int[1];
      GLFW.glfwGetWindowSize(handle, sw, sh);
      GLFW.glfwGetWindowPos(handle, px, py);
      savedW = sw[0];
      savedH = sh[0];
      savedX = px[0];
      savedY = py[0];
      savedMaximized =
          GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
      hasSavedState = true;

      client.execute(() -> GLFW.glfwIconifyWindow(handle));
    }
  }

  /**
   * Restores the window from a minimised state, focuses it, and requests the
   * user's attention. Safe to call when the window is not minimised.
   */
  public static void restoreWindow() {
    Minecraft client = Minecraft.getInstance();
    if (client.getWindow() == null) {
      return;
    }

    long handle = client.getWindow().handle();
    boolean isMinimized =
        GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;

    if (isMinimized) {
      client.execute(
          () -> {
            GLFW.glfwRestoreWindow(handle);
            GLFW.glfwFocusWindow(handle);
            GLFW.glfwRequestWindowAttention(handle);
          });
    }
  }

  /**
   * Focuses the window and requests attention. Does nothing if the window is
   * currently minimised.
   */
  public static void requestAttention() {
    Minecraft client = Minecraft.getInstance();
    if (client.getWindow() == null) {
      return;
    }

    long handle = client.getWindow().handle();
    boolean isMinimized =
        GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;

    if (!isMinimized) {
      client.execute(
          () -> {
            long h = client.getWindow().handle();
            GLFW.glfwFocusWindow(h);
            GLFW.glfwRequestWindowAttention(h);
          });
    }
  }
}
