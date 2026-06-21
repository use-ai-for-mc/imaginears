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
  private static final double LIVE_DISPATCH_DISTANCE = 1.0;
  private static final double REFERENCE_DISPATCH_DISTANCE = 1.0;

  private static final RideReference TRON_REFERENCE = loadReference();

  private static int activeVehicleId = -1;
  private static int scanCooldownTicks = 0;
  private static boolean hasStationAnchor = false;
  private static double stationAnchorX;
  private static double stationAnchorY;
  private static double stationAnchorZ;
  private static boolean dispatched = false;
  private static long dispatchStartedAtMs = 0;
  private static double activeCountdownDurationSeconds = 0.0;
  private static RideMarkerDetector.RideIdentity activeIdentity;
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
      anchorVehicle(vehicle);
    }
    updateDispatchState(vehicle);

    if (scanCooldownTicks > 0) {
      scanCooldownTicks--;
    }
    if (activeIdentity == null && scanCooldownTicks <= 0) {
      scanCooldownTicks = MARKER_SCAN_INTERVAL_TICKS;
      List<RideMarkerDetector.MarkerInfo> markers =
          RideMarkerDetector.scanMarkers(client, vehicle);
      RideMarkerDetector.RideIdentity identity = RideMarkerDetector.identifyRide(markers);
      activeIdentity = RideMarkerDetector.isTronLightcycle(identity) ? identity : null;
    }

    if (activeIdentity == null) {
      lastEstimate = null;
      return;
    }

    lastEstimate = estimate(activeIdentity);
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

  private static Estimate estimate(RideMarkerDetector.RideIdentity identity) {
    if (TRON_REFERENCE.traces().isEmpty()) {
      return null;
    }

    double duration = movementDurationSeconds(identity.markerDamage());
    if (duration <= 0.0) {
      return null;
    }

    if (!dispatched) {
      return new Estimate(
          identity.rideName(),
          identity.markerDamage(),
          identity.vehicleVariant(),
          0,
          0,
          duration,
          false);
    }

    if (activeCountdownDurationSeconds <= 0.0) {
      activeCountdownDurationSeconds = duration;
    }
    duration = activeCountdownDurationSeconds;

    double elapsed = Math.min(duration, Math.max(0.0, elapsedSinceDispatchSeconds()));
    double progress = duration <= 0.0 ? 0.0 : Math.min(100.0, elapsed / duration * 100.0);
    double remaining = Math.max(0.0, duration - elapsed);

    return new Estimate(
        identity.rideName(),
        identity.markerDamage(),
        identity.vehicleVariant(),
        progress,
        elapsed,
        remaining,
        true);
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

  private static double distance(Point a, Point b) {
    double dx = a.x() - b.x();
    double dy = a.y() - b.y();
    double dz = a.z() - b.z();
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private static double distance(
      double ax, double ay, double az, double bx, double by, double bz) {
    double dx = ax - bx;
    double dy = ay - by;
    double dz = az - bz;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private static void anchorVehicle(Entity vehicle) {
    stationAnchorX = vehicle.getX();
    stationAnchorY = vehicle.getY();
    stationAnchorZ = vehicle.getZ();
    hasStationAnchor = true;
    dispatched = false;
    dispatchStartedAtMs = 0;
  }

  private static void updateDispatchState(Entity vehicle) {
    if (!hasStationAnchor) {
      anchorVehicle(vehicle);
      return;
    }
    if (dispatched) {
      return;
    }

    // The station can hold several rows of identical vehicles. Start the timer from the mounted
    // vehicle's own movement, not from a nearby reference point or a neighboring vehicle.
    double distanceFromAnchor =
        distance(
            vehicle.getX(),
            vehicle.getY(),
            vehicle.getZ(),
            stationAnchorX,
            stationAnchorY,
            stationAnchorZ);
    if (distanceFromAnchor >= LIVE_DISPATCH_DISTANCE) {
      dispatched = true;
      dispatchStartedAtMs = System.currentTimeMillis();
    }
  }

  private static double elapsedSinceDispatchSeconds() {
    if (dispatchStartedAtMs <= 0) {
      return 0.0;
    }
    return Math.max(0.0, (System.currentTimeMillis() - dispatchStartedAtMs) / 1000.0);
  }

  private static double movementDurationSeconds(int markerDamage) {
    List<Trace> candidates = candidateTraces(markerDamage);
    if (candidates.isEmpty()) {
      candidates = TRON_REFERENCE.traces();
    }
    return candidates.stream()
        .mapToDouble(Trace::movingDurationSeconds)
        .filter(duration -> duration > 0.0)
        .average()
        .orElse(0.0);
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
            double duration =
                profile.optDouble("durationSeconds", points.get(points.size() - 1).t());
            double dispatchStartSeconds = dispatchStartSeconds(points);
            traces.add(
                new Trace(
                    profile.optString("sourceFile", "unknown"),
                    profile.optInt("markerDamage", -1),
                    profile.optString("vehicleVariant", ""),
                    Math.max(0.0, duration - dispatchStartSeconds)));
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

  private static double dispatchStartSeconds(List<Point> points) {
    if (points.isEmpty()) {
      return 0.0;
    }
    Point origin = points.get(0);
    for (Point point : points) {
      if (distance(origin, point) >= REFERENCE_DISPATCH_DISTANCE) {
        return point.t();
      }
    }
    return 0.0;
  }

  private static void reset() {
    activeVehicleId = -1;
    scanCooldownTicks = 0;
    hasStationAnchor = false;
    stationAnchorX = 0.0;
    stationAnchorY = 0.0;
    stationAnchorZ = 0.0;
    dispatched = false;
    dispatchStartedAtMs = 0;
    activeCountdownDurationSeconds = 0.0;
    activeIdentity = null;
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
      double movingDurationSeconds) {}

  private record Point(double t, double x, double y, double z) {}

  public record Estimate(
      String rideName,
      int markerDamage,
      String vehicleVariant,
      double progressPercent,
      double elapsedSeconds,
      double remainingSeconds,
      boolean counting) {
    String format() {
      if (!counting) {
        return displayName() + ": waiting for dispatch.";
      }
      return String.format(
          "%s: %.0f%% complete, %s remaining.",
          displayName(), progressPercent, formatSeconds(remainingSeconds));
    }

    private String displayName() {
      return vehicleVariant == null || vehicleVariant.isBlank()
          ? rideName
          : rideName + " " + vehicleVariant;
    }
  }
}
