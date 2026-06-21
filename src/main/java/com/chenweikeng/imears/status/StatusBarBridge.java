package com.chenweikeng.imears.status;

import com.chenweikeng.imears.NativeHelperExtractor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the native status helper process and sends short text updates over stdin.
 *
 * <p>The helper is a macOS menu bar item or Windows taskbar-adjacent overlay. It accepts newline
 * delimited JSON commands:
 *
 * <pre>
 *   {"cmd":"set","text":"2:45"}
 *   {"cmd":"quit"}
 * </pre>
 */
public class StatusBarBridge {
  private static final Logger LOGGER = LoggerFactory.getLogger("imears/status-helper");
  private static final long READY_TIMEOUT_SECONDS = 5;

  private Process process;
  private BufferedWriter writer;
  private Thread readerThread;
  private volatile boolean running;
  private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
  private final StringBuilder stderrTail = new StringBuilder();

  public boolean start() {
    Path helperPath = findHelperBinary();
    if (helperPath == null) {
      LOGGER.warn("Status helper binary not found; tray countdown disabled");
      return false;
    }

    Thread stderrThread = null;
    try {
      ProcessBuilder pb = new ProcessBuilder(helperPath.toAbsolutePath().toString());
      pb.redirectErrorStream(false);
      pb.environment().put("DOTNET_DISABLE_GUI_ERRORS", "1");
      process = pb.start();

      writer =
          new BufferedWriter(
              new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

      running = true;
      readerThread = new Thread(this::readLoop, "ImearsStatusBarBridge-Reader");
      readerThread.setDaemon(true);
      readerThread.start();

      stderrThread = new Thread(this::drainStderr, "ImearsStatusBarBridge-Stderr");
      stderrThread.setDaemon(true);
      stderrThread.start();

      try {
        readyFuture.get(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (Exception e) {
        try {
          stderrThread.join(500);
        } catch (InterruptedException ignore) {
          Thread.currentThread().interrupt();
        }
        String diag;
        synchronized (stderrTail) {
          diag = stderrTail.toString().trim();
        }
        if (diag.isEmpty()) {
          LOGGER.warn(
              "Status helper did not become ready within {}s; tray countdown disabled."
                  + " On Windows this usually means no compatible .NET Desktop Runtime.",
              READY_TIMEOUT_SECONDS);
        } else {
          LOGGER.warn("Status helper failed to start; tray countdown disabled. Reason: {}", diag);
        }
        stop();
        return false;
      }

      LOGGER.info("Status helper started (pid={})", process.pid());
      return true;
    } catch (IOException e) {
      LOGGER.warn("Failed to start status helper process: {}", e.getMessage());
      return false;
    }
  }

  public void setText(String text) {
    sendCommand(new JSONObject().put("cmd", "set").put("text", text));
  }

  public void stop() {
    running = false;
    try {
      sendCommand(new JSONObject().put("cmd", "quit"));
    } catch (Exception ignore) {
      // Best effort.
    }
    if (process != null) {
      try {
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
      }
      process = null;
    }
    writer = null;
  }

  public boolean isRunning() {
    return running && process != null && process.isAlive();
  }

  private void sendCommand(JSONObject command) {
    if (writer == null || !isRunning()) {
      return;
    }
    try {
      synchronized (this) {
        writer.write(command.toString());
        writer.newLine();
        writer.flush();
      }
    } catch (IOException e) {
      LOGGER.warn("Failed to send command to status helper: {}", e.getMessage());
    }
  }

  private void readLoop() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while (running && (line = reader.readLine()) != null) {
        try {
          JSONObject json = new JSONObject(line);
          String type = json.optString("type", "");
          switch (type) {
            case "ready" -> readyFuture.complete(null);
            case "error" -> LOGGER.warn("Status helper error: {}", json.optString("message", ""));
            default -> {
              // ignored
            }
          }
        } catch (Exception e) {
          LOGGER.debug("Unparseable status helper output: {}", line);
        }
      }
    } catch (IOException e) {
      if (running) {
        LOGGER.warn("Status helper stdout read error: {}", e.getMessage());
      }
    } finally {
      if (!readyFuture.isDone()) {
        readyFuture.completeExceptionally(
            new IllegalStateException("status helper exited before signaling ready"));
      }
    }
    running = false;
  }

  private void drainStderr() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        synchronized (stderrTail) {
          if (stderrTail.length() > 0) {
            stderrTail.append(" | ");
          }
          stderrTail.append(line);
        }
      }
    } catch (IOException ignore) {
      // Process gone.
    }
  }

  private Path findHelperBinary() {
    String os = System.getProperty("os.name", "").toLowerCase();
    boolean isMac = os.contains("mac") || os.contains("darwin");
    boolean isWin = os.contains("win");
    if (!isMac && !isWin) {
      return null;
    }

    String platform = isMac ? "macos" : "windows";
    String binaryName = isMac ? "status-helper" : "status-helper.exe";
    Path dir =
        FabricLoader.getInstance().getConfigDir().resolve("imaginears").resolve("native");
    return NativeHelperExtractor.findOrExtract(
        StatusBarBridge.class,
        "/native/" + platform + "/" + binaryName,
        dir.resolve(binaryName),
        true);
  }
}
