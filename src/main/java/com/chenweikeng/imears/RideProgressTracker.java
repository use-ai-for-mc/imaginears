package com.chenweikeng.imears;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RideProgressTracker {
  private static final Logger LOGGER = LoggerFactory.getLogger("imears/ride-progress");
  private static final String TRON_REFERENCE_RESOURCE =
      "/assets/imears/rides/tron-lightcycle-reference.json";
  private static final int MARKER_SCAN_INTERVAL_TICKS = 20;
  private static final double GOOD_MATCH_DISTANCE = 8.0;
  private static final double ACCEPTABLE_MATCH_DISTANCE = 16.0;

  private static final RideReference TRON_REFERENCE = loadReference();

  private static int activeVehicleId = -1;
  private static int scanCooldownTicks = 0;
  private static RideMarkerDetector.RideIdentity activeIdentity;
  private static Trace activeTrace;
  private static int lastPointIndex = -1;
  private static Estimate lastEstimate;

  private RideProgressTracker() {}

  public static void tick(Minecraft client, boolean onVehicle) {
    if (!ServerState.isImaginearsServer(client) || client.player == null || client.level == null) {
      reset();
      return;
    }

    if (!onVehicle) {
      reset();
      return;
    }

    Entity vehicle = client.player.getVehicle();
    if (vehicle == null) {
      reset();
      return;
    }

    if (vehicle.getId() != activeVehicleId) {
      reset();
      activeVehicleId = vehicle.getId();
    }

    if (scanCooldownTicks > 0) {
      scanCooldownTicks--;
    }
    if (activeIdentity == null || scanCooldownTicks <= 0) {
      scanCooldownTicks = MARKER_SCAN_INTERVAL_TICKS;
      List<RideMarkerDetector.MarkerInfo> markers =
          RideMarkerDetector.scanMarkers(client, vehicle);
      RideMarkerDetector.RideIdentity identity = RideMarkerDetector.identifyRide(markers);
      activeIdentity = RideMarkerDetector.isTronLightcycle(identity) ? identity : null;
    }

    if (activeIdentity == null) {
      lastEstimate = null;
      activeTrace = null;
      lastPointIndex = -1;
      return;
    }

    lastEstimate = estimate(vehicle, activeIdentity);
  }

  public static void reportStatus(Minecraft client) {
    if (client == null || client.player == null) {
      return;
    }
    if (lastEstimate == null) {
      notifyUser(client, "Ride progress is idle.");
      return;
    }
    notifyUser(client, lastEstimate.format());
  }

  public static void resetCommand(Minecraft client) {
    reset();
    notifyUser(client, "Ride progress tracker reset.");
  }

  public static Estimate lastEstimate() {
    return lastEstimate;
  }

  private static Estimate estimate(
      Entity vehicle, RideMarkerDetector.RideIdentity identity) {
    if (TRON_REFERENCE.traces().isEmpty()) {
      return null;
    }

    Match match = findBestMatch(vehicle.getX(), vehicle.getY(), vehicle.getZ(), identity);
    if (match == null || match.distance() > ACCEPTABLE_MATCH_DISTANCE) {
      return new Estimate(
          identity.rideName(),
          identity.markerDamage(),
          identity.vehicleVariant(),
          "",
          0,
          0,
          0,
          match == null ? -1.0 : match.distance(),
          false);
    }

    activeTrace = match.trace();
    lastPointIndex = match.pointIndex();

    double elapsed = match.point().t();
    double duration = match.trace().durationSeconds();
    double progress = duration <= 0.0 ? 0.0 : Math.min(100.0, Math.max(0.0, elapsed / duration * 100.0));
    double remaining = Math.max(0.0, duration - elapsed);
    return new Estimate(
        identity.rideName(),
        identity.markerDamage(),
        identity.vehicleVariant(),
        match.trace().sourceFile(),
        progress,
        elapsed,
        remaining,
        match.distance(),
        match.distance() <= GOOD_MATCH_DISTANCE);
  }

  private static Match findBestMatch(
      double x, double y, double z, RideMarkerDetector.RideIdentity identity) {
    List<Trace> candidates = candidateTraces(identity.markerDamage());
    if (candidates.isEmpty()) {
      candidates = TRON_REFERENCE.traces();
    }

    Match sameTraceMatch = null;
    if (activeTrace != null && candidates.contains(activeTrace)) {
      sameTraceMatch = findBestMatchInTrace(activeTrace, x, y, z, Math.max(0, lastPointIndex - 2));
      if (sameTraceMatch != null && sameTraceMatch.distance() <= GOOD_MATCH_DISTANCE) {
        return sameTraceMatch;
      }
    }

    Match best = null;
    for (Trace trace : candidates) {
      int minIndex = trace == activeTrace ? Math.max(0, lastPointIndex - 2) : 0;
      Match candidate = findBestMatchInTrace(trace, x, y, z, minIndex);
      if (candidate != null && (best == null || candidate.distance() < best.distance())) {
        best = candidate;
      }
    }
    if (best != null) {
      return best;
    }
    return sameTraceMatch;
  }

  private static List<Trace> candidateTraces(int markerDamage) {
    if (markerDamage < 0) {
      return TRON_REFERENCE.traces();
    }
    List<Trace> traces =
        TRON_REFERENCE.traces().stream()
            .filter(trace -> trace.markerDamage() == markerDamage)
            .toList();
    return traces.isEmpty() ? TRON_REFERENCE.traces() : traces;
  }

  private static Match findBestMatchInTrace(
      Trace trace, double x, double y, double z, int minIndex) {
    Match best = null;
    List<Point> points = trace.points();
    for (int i = Math.max(0, minIndex); i < points.size(); i++) {
      Point point = points.get(i);
      double distance = distance(point, x, y, z);
      if (best == null || distance < best.distance()) {
        best = new Match(trace, point, i, distance);
      }
    }
    return best;
  }

  private static double distance(Point point, double x, double y, double z) {
    double dx = point.x() - x;
    double dy = point.y() - y;
    double dz = point.z() - z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private static RideReference loadReference() {
    try (InputStream in = RideProgressTracker.class.getResourceAsStream(TRON_REFERENCE_RESOURCE)) {
      if (in == null) {
        LOGGER.warn("Missing ride reference resource: {}", TRON_REFERENCE_RESOURCE);
        return new RideReference(RideMarkerDetector.TRON_LIGHTCYCLE_KEY, List.of());
      }
      JSONObject root =
          new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      JSONArray profileJson = root.optJSONArray("profiles");
      List<Trace> traces = new ArrayList<>();
      if (profileJson != null) {
        for (int i = 0; i < profileJson.length(); i++) {
          JSONObject profile = profileJson.getJSONObject(i);
          JSONArray pointJson = profile.getJSONArray("points");
          List<Point> points = new ArrayList<>();
          for (int j = 0; j < pointJson.length(); j++) {
            JSONObject point = pointJson.getJSONObject(j);
            points.add(
                new Point(
                    point.getDouble("t"),
                    point.getDouble("x"),
                    point.getDouble("y"),
                    point.getDouble("z")));
          }
          if (!points.isEmpty()) {
            traces.add(
                new Trace(
                    profile.optString("sourceFile", "unknown"),
                    profile.optInt("markerDamage", -1),
                    profile.optString("vehicleVariant", ""),
                    profile.optDouble("durationSeconds", points.get(points.size() - 1).t()),
                    List.copyOf(points)));
          }
        }
      }
      traces.sort(
          Comparator.comparingInt(Trace::markerDamage).thenComparing(Trace::sourceFile));
      LOGGER.info("Loaded {} TRON reference traces", traces.size());
      return new RideReference(root.optString("rideKey", "tron-lightcycle"), List.copyOf(traces));
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Failed to load TRON ride reference", e);
      return new RideReference(RideMarkerDetector.TRON_LIGHTCYCLE_KEY, List.of());
    }
  }

  private static void reset() {
    activeVehicleId = -1;
    scanCooldownTicks = 0;
    activeIdentity = null;
    activeTrace = null;
    lastPointIndex = -1;
    lastEstimate = null;
  }

  private static void notifyUser(Minecraft client, String message) {
    if (client == null) {
      return;
    }
    client.gui.getChat().addClientSystemMessage(Component.literal("\u00A76[IMEARS] \u00A7f" + message));
  }

  private static String formatSeconds(double seconds) {
    int total = Math.max(0, (int) Math.round(seconds));
    return String.format("%d:%02d", total / 60, total % 60);
  }

  private record RideReference(String rideKey, List<Trace> traces) {}

  private record Trace(
      String sourceFile,
      int markerDamage,
      String vehicleVariant,
      double durationSeconds,
      List<Point> points) {}

  private record Point(double t, double x, double y, double z) {}

  private record Match(Trace trace, Point point, int pointIndex, double distance) {}

  public record Estimate(
      String rideName,
      int markerDamage,
      String vehicleVariant,
      String sourceFile,
      double progressPercent,
      double elapsedSeconds,
      double remainingSeconds,
      double matchDistance,
      boolean confident) {
    String format() {
      if (sourceFile == null || sourceFile.isBlank()) {
        return String.format(
            "%s detected, but no reference match is close enough (nearest %.1fm).",
            rideName, matchDistance);
      }
      return String.format(
          "%s %s: %.0f%% complete, %s remaining (match %.1fm%s).",
          rideName,
          vehicleVariant == null || vehicleVariant.isBlank() ? "" : vehicleVariant,
          progressPercent,
          formatSeconds(remainingSeconds),
          matchDistance,
          confident ? "" : ", low confidence");
    }
  }
}
