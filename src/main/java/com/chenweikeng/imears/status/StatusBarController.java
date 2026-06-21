package com.chenweikeng.imears.status;

import com.chenweikeng.imears.RideProgressTracker;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drives the native status helper with the current TRON ride countdown. */
public final class StatusBarController {
  private static final Logger LOGGER = LoggerFactory.getLogger("imears/status-controller");
  private static final StatusBarController INSTANCE = new StatusBarController();
  private static final String NO_TIMING_PLACEHOLDER = "--:--";

  public static StatusBarController getInstance() {
    return INSTANCE;
  }

  private volatile StatusBarBridge bridge;
  private final AtomicBoolean starting = new AtomicBoolean(false);
  private volatile boolean shutdownHookRegistered;
  private volatile boolean disabledForSession;
  private volatile String lastTextSent;

  private StatusBarController() {}

  public void tick(Minecraft client) {
    RideProgressTracker.Estimate estimate = RideProgressTracker.lastEstimate();
    if (estimate == null || !estimate.counting()) {
      sendDesiredText("");
      return;
    }

    ensureStarted();

    sendDesiredText(computeText(estimate));
  }

  public void onDisconnect() {
    clear();
  }

  public void shutdown() {
    lastTextSent = null;
    StatusBarBridge b = bridge;
    bridge = null;
    if (b != null) {
      b.stop();
    }
  }

  private void clear() {
    sendDesiredText("");
  }

  private void sendDesiredText(String text) {
    if (!text.equals(lastTextSent) && sendIfRunning(text)) {
      lastTextSent = text;
    }
  }

  private static String computeText(RideProgressTracker.Estimate estimate) {
    if (!estimate.counting()) {
      return NO_TIMING_PLACEHOLDER;
    }
    int remaining = Math.max(0, (int) Math.round(estimate.remainingSeconds()));
    return formatMinutesSeconds(remaining);
  }

  private static String formatMinutesSeconds(int totalSeconds) {
    int minutes = totalSeconds / 60;
    int seconds = totalSeconds % 60;
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
  }

  private boolean sendIfRunning(String text) {
    StatusBarBridge b = bridge;
    if (b != null && b.isRunning()) {
      b.setText(text);
      return true;
    }
    return false;
  }

  private void ensureStarted() {
    if (disabledForSession) {
      return;
    }
    StatusBarBridge existing = bridge;
    if (existing != null && existing.isRunning()) {
      return;
    }
    if (!starting.compareAndSet(false, true)) {
      return;
    }
    Thread t =
        new Thread(
            () -> {
              try {
                StatusBarBridge b = new StatusBarBridge();
                if (b.start()) {
                  bridge = b;
                  lastTextSent = null;
                  registerShutdownHookOnce();
                } else {
                  disabledForSession = true;
                  LOGGER.info("Status helper unavailable; tray countdown disabled this session");
                }
              } catch (RuntimeException e) {
                disabledForSession = true;
                LOGGER.warn("Unexpected error starting status helper; tray countdown disabled", e);
              } finally {
                starting.set(false);
              }
            },
            "ImearsStatusBarController-Starter");
    t.setDaemon(true);
    t.start();
  }

  private void registerShutdownHookOnce() {
    if (shutdownHookRegistered) {
      return;
    }
    shutdownHookRegistered = true;
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  StatusBarBridge b = bridge;
                  if (b != null) {
                    b.stop();
                  }
                },
                "ImearsStatusBarController-Shutdown"));
  }
}
