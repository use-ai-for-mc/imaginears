package com.chenweikeng.imears;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records raw Imaginears ride sessions so ride markers and durations can be learned later. */
public final class RideProfiler {
  private static final Logger LOGGER = LoggerFactory.getLogger("imears/ride-profiler");
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
  private static final int SAMPLE_INTERVAL_TICKS = 40;

  private static boolean recording;
  private static JSONObject session;
  private static long startTimeMs;
  private static int tickCounter;
  private static int activeVehicleId = -1;
  private static String rideKey = RideMarkerDetector.UNKNOWN_RIDE_KEY;
  private static String primaryMarkerKey = RideMarkerDetector.UNKNOWN_RIDE_KEY;
  private static int pathSamples;
  private static int markerSnapshots;

  private RideProfiler() {}

  public static void tick(Minecraft client, boolean onVehicle) {
    if (!ServerState.isImaginearsServer(client) || client.player == null || client.level == null) {
      reset(client, "left_server");
      return;
    }

    if (!onVehicle) {
      if (recording) {
        stop(client, "dismount");
      }
      return;
    }

    Entity vehicle = client.player.getVehicle();
    if (vehicle == null) {
      if (recording) {
        stop(client, "vehicle_missing");
      }
      return;
    }

    if (!recording) {
      start(client, vehicle);
    } else if (vehicle.getId() != activeVehicleId) {
      activeVehicleId = vehicle.getId();
      appendVehicleEvent(vehicle);
      appendMarkerSnapshot(client, vehicle);
    }

    tickCounter++;
    if (tickCounter >= SAMPLE_INTERVAL_TICKS) {
      tickCounter = 0;
      appendPositionSample(client, vehicle);
      appendMarkerSnapshot(client, vehicle);
    }
  }

  public static void recordChatMessage(Component message, boolean overlay) {
    if (!recording || session == null || message == null) {
      return;
    }
    String text = OpenAudioText.strip(message.getString());
    if (text.isBlank()) {
      return;
    }
    JSONObject entry =
        new JSONObject()
            .put("t", elapsedSeconds())
            .put("text", text)
            .put("overlay", overlay);
    session.getJSONArray("chatLog").put(entry);
  }

  public static void reportStatus(Minecraft client) {
    if (client == null || client.player == null) {
      return;
    }
    if (!recording) {
      notifyUser(client, "Ride profiler is idle.");
      return;
    }
    notifyUser(
        client,
        String.format(
            "Profiling %s for %.1fs (%d path samples, %d marker snapshots).",
            displayRideKey(), elapsedSeconds(), pathSamples, markerSnapshots));
  }

  public static void flush(Minecraft client) {
    if (!recording) {
      reportStatus(client);
      return;
    }
    stop(client, "manual_flush");
  }

  public static void reset(Minecraft client, String reason) {
    if (recording) {
      stop(client, reason);
    }
    recording = false;
    session = null;
    startTimeMs = 0;
    tickCounter = 0;
    activeVehicleId = -1;
    rideKey = RideMarkerDetector.UNKNOWN_RIDE_KEY;
    primaryMarkerKey = RideMarkerDetector.UNKNOWN_RIDE_KEY;
    pathSamples = 0;
    markerSnapshots = 0;
  }

  private static void start(Minecraft client, Entity vehicle) {
    recording = true;
    startTimeMs = System.currentTimeMillis();
    tickCounter = 0;
    activeVehicleId = vehicle.getId();
    pathSamples = 0;
    markerSnapshots = 0;

    List<RideMarkerDetector.MarkerInfo> initialMarkerInfos =
        RideMarkerDetector.scanMarkers(client, vehicle);
    JSONArray initialMarkers = RideMarkerDetector.markersToJson(initialMarkerInfos);
    RideMarkerDetector.RideIdentity identity = RideMarkerDetector.identifyRide(initialMarkerInfos);
    rideKey = identity.rideKey();
    primaryMarkerKey = identity.primaryMarkerKey();

    session =
        new JSONObject()
            .put("schemaVersion", "2")
            .put("startedAt", Instant.now().toString())
            .put("playerName", client.getUser().getName())
            .put("serverIp", client.getCurrentServer() != null ? client.getCurrentServer().ip : "")
            .put("dimension", client.level.dimension().toString())
            .put("rideKey", rideKey)
            .put("rideName", identity.rideName())
            .put("primaryMarkerKey", primaryMarkerKey)
            .put("rideEvidence", identity.evidence())
            .put("vehicle", vehicleJson(vehicle))
            .put("initialMarkers", initialMarkers)
            .put("scoreboardAtStart", scoreboardJson(client))
            .put("path", new JSONArray())
            .put("markerSnapshots", new JSONArray())
            .put("vehicleEvents", new JSONArray())
            .put("chatLog", new JSONArray());

    appendVehicleEvent(vehicle);
    appendPositionSample(client, vehicle);
    appendMarkerSnapshot(client, vehicle);

    LOGGER.info("Started Imaginears ride profiling: {} ({})", displayRideKey(), primaryMarkerKey);
    notifyUser(client, "Started ride profile: " + displayRideKey());
  }

  private static void stop(Minecraft client, String reason) {
    if (!recording || session == null) {
      return;
    }
    long durationMs = System.currentTimeMillis() - startTimeMs;
    session
        .put("endedAt", Instant.now().toString())
        .put("stopReason", reason)
        .put("durationMs", durationMs)
        .put("durationFormatted", formatDuration(durationMs))
        .put("scoreboardAtEnd", scoreboardJson(client));

    JSONObject completed = session;
    String fileKey =
        RideMarkerDetector.UNKNOWN_RIDE_KEY.equals(rideKey) ? primaryMarkerKey : rideKey;
    recording = false;
    session = null;
    startTimeMs = 0;
    tickCounter = 0;
    activeVehicleId = -1;
    rideKey = RideMarkerDetector.UNKNOWN_RIDE_KEY;
    primaryMarkerKey = RideMarkerDetector.UNKNOWN_RIDE_KEY;

    Thread writer =
        new Thread(() -> writeSession(client, completed, fileKey), "imears-ride-profile-writer");
    writer.setDaemon(true);
    writer.start();
  }

  private static void writeSession(Minecraft client, JSONObject data, String markerKey) {
    try {
      Path dir = client.gameDirectory.toPath().resolve("logs").resolve("imears-ride-sessions");
      Files.createDirectories(dir);
      String timestamp = LocalDateTime.now().format(TS_FMT);
      Path out = dir.resolve(safeFileName(markerKey) + "-" + timestamp + ".json");
      Files.writeString(out, data.toString(2));
      LOGGER.info("Wrote Imaginears ride profile -> {}", out);
      client.execute(() -> notifyUser(client, "Ride profile saved: " + out.getFileName()));
    } catch (IOException e) {
      LOGGER.error("Failed to write Imaginears ride profile", e);
    }
  }

  private static void appendPositionSample(Minecraft client, Entity vehicle) {
    if (session == null || client.player == null) {
      return;
    }
    JSONObject sample =
        new JSONObject()
            .put("t", elapsedSeconds())
            .put("x", round3(vehicle.getX()))
            .put("y", round3(vehicle.getY()))
            .put("z", round3(vehicle.getZ()))
            .put("yaw", round1(client.player.getYRot()))
            .put("pitch", round1(client.player.getXRot()));
    session.getJSONArray("path").put(sample);
    pathSamples++;
  }

  private static void appendMarkerSnapshot(Minecraft client, Entity vehicle) {
    if (session == null) {
      return;
    }
    session
        .getJSONArray("markerSnapshots")
        .put(new JSONObject().put("t", elapsedSeconds()).put("markers", scanMarkers(client, vehicle)));
    markerSnapshots++;
  }

  private static void appendVehicleEvent(Entity vehicle) {
    if (session == null) {
      return;
    }
    session
        .getJSONArray("vehicleEvents")
        .put(new JSONObject().put("t", elapsedSeconds()).put("vehicle", vehicleJson(vehicle)));
  }

  private static JSONArray scanMarkers(Minecraft client, Entity vehicle) {
    return RideMarkerDetector.markersToJson(RideMarkerDetector.scanMarkers(client, vehicle));
  }

  private static JSONObject scoreboardJson(Minecraft client) {
    JSONObject out = new JSONObject();
    if (client == null || client.level == null) {
      return out;
    }
    try {
      Scoreboard scoreboard = client.level.getScoreboard();
      Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      if (sidebar == null) {
        return out;
      }
      out.put("title", sidebar.getDisplayName().getString());
      JSONArray entries = new JSONArray();
      for (PlayerScoreEntry score : scoreboard.listPlayerScores(sidebar)) {
        JSONObject entry =
            new JSONObject()
                .put("value", score.value())
                .put("owner", OpenAudioText.strip(score.owner()))
                .put("ownerName", OpenAudioText.strip(score.ownerName().getString()))
                .put("hidden", score.isHidden());
        if (score.display() != null) {
          entry.put("display", OpenAudioText.strip(score.display().getString()));
        }
        entries.put(entry);
      }
      out.put("entries", entries);
    } catch (RuntimeException e) {
      LOGGER.debug("Failed to snapshot scoreboard", e);
    }
    return out;
  }

  private static JSONObject vehicleJson(Entity vehicle) {
    return new JSONObject()
        .put("entityId", vehicle.getId())
        .put("type", vehicle.getType().toShortString())
        .put("x", round3(vehicle.getX()))
        .put("y", round3(vehicle.getY()))
        .put("z", round3(vehicle.getZ()));
  }

  private static String displayRideKey() {
    return RideMarkerDetector.UNKNOWN_RIDE_KEY.equals(rideKey) ? primaryMarkerKey : rideKey;
  }

  private static String safeFileName(String value) {
    String safe = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
    return safe.isBlank() ? "unknown" : safe;
  }

  private static double elapsedSeconds() {
    return round1((System.currentTimeMillis() - startTimeMs) / 1000.0);
  }

  private static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }

  private static String formatDuration(long ms) {
    long s = ms / 1000;
    return String.format("%dm %ds", s / 60, s % 60);
  }

  private static void notifyUser(Minecraft client, String message) {
    if (client == null) {
      return;
    }
    client.gui.getChat().addClientSystemMessage(Component.literal("\u00A76[IMEARS] \u00A7f" + message));
  }

  private static final class OpenAudioText {
    private OpenAudioText() {}

    static String strip(String value) {
      return value == null ? "" : value.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
    }
  }
}
