package com.chenweikeng.imears;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RideCountdownHudRenderer {
  private static final int TIME_COLOR_NORMAL = 0x55FF55;
  private static final int TIME_COLOR_WARNING = 0xFFFF55;
  private static final int TIME_COLOR_ENDING = 0xFF5555;
  private static final int MIN_BAR_WIDTH = 150;
  private static final int BAR_HEIGHT = 3;
  private static final int TOP_MARGIN = 5;

  private RideCountdownHudRenderer() {}

  public static void extract(GuiGraphicsExtractor graphics) {
    Minecraft client = Minecraft.getInstance();
    if (client.player == null || client.options.hideGui) {
      return;
    }
    if (client.screen != null && !client.screen.isInGameUi()) {
      return;
    }

    RideProgressTracker.Estimate estimate = RideProgressTracker.lastEstimate();
    if (estimate == null || estimate.sourceFile() == null || estimate.sourceFile().isBlank()) {
      return;
    }

    int remainingSeconds = Math.max(0, (int) Math.round(estimate.remainingSeconds()));
    int progress = Math.min(100, Math.max(0, (int) Math.round(estimate.progressPercent())));
    String displayText =
        String.format(
            Locale.ROOT,
            "%s (%d%%, %s left)",
            estimate.rideName(),
            progress,
            formatDuration(remainingSeconds));

    Font font = client.font;
    int timeColor = timeColor(remainingSeconds);
    int screenWidth = graphics.guiWidth();
    int textWidth = font.width(displayText);
    int x = (screenWidth - textWidth) / 2;
    int y = TOP_MARGIN;

    graphics.text(font, displayText, x, y, timeColor, true);

    int barWidth = Math.max(textWidth, MIN_BAR_WIDTH);
    int barX = (screenWidth - barWidth) / 2;
    int barY = y + font.lineHeight + 2;
    graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0x80000000);

    int fillWidth = barWidth * progress / 100;
    if (fillWidth > 0) {
      graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, 0xFF000000 | timeColor);
    }
  }

  private static int timeColor(int remainingSeconds) {
    if (remainingSeconds <= 10) {
      return TIME_COLOR_ENDING;
    }
    if (remainingSeconds <= 60) {
      return TIME_COLOR_WARNING;
    }
    return TIME_COLOR_NORMAL;
  }

  private static String formatDuration(int totalSeconds) {
    if (totalSeconds <= 0) {
      return "0 sec";
    }

    int minutes = totalSeconds / 60;
    int seconds = totalSeconds % 60;
    if (minutes > 0) {
      return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
    return String.format(Locale.ROOT, "%d sec", seconds);
  }
}
