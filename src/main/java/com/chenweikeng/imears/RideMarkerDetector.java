package com.chenweikeng.imears;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.json.JSONArray;
import org.json.JSONObject;

public final class RideMarkerDetector {
  public static final String UNKNOWN_RIDE_KEY = "unknown";
  public static final String TRON_LIGHTCYCLE_KEY = "tron-lightcycle";
  public static final String TRON_LIGHTCYCLE_NAME = "TRON Lightcycle";

  private static final double MARKER_SCAN_RADIUS = 10.0;
  private static final int MAX_MARKERS_PER_SAMPLE = 64;
  private static final double TRON_MARKER_MAX_PLAYER_DISTANCE = 1.75;
  private static final EquipmentSlot[] MARKER_SLOTS = {
    EquipmentSlot.HEAD,
    EquipmentSlot.MAINHAND,
    EquipmentSlot.OFFHAND,
    EquipmentSlot.CHEST,
    EquipmentSlot.LEGS,
    EquipmentSlot.FEET
  };

  private RideMarkerDetector() {}

  public static List<MarkerInfo> scanMarkers(Minecraft client, Entity vehicle) {
    List<MarkerInfo> markers = new ArrayList<>();
    if (client == null || client.level == null || vehicle == null) {
      return markers;
    }

    AABB box =
        new AABB(
            vehicle.getX() - MARKER_SCAN_RADIUS,
            vehicle.getY() - MARKER_SCAN_RADIUS,
            vehicle.getZ() - MARKER_SCAN_RADIUS,
            vehicle.getX() + MARKER_SCAN_RADIUS,
            vehicle.getY() + MARKER_SCAN_RADIUS,
            vehicle.getZ() + MARKER_SCAN_RADIUS);

    for (ArmorStand armorStand : client.level.getEntitiesOfClass(ArmorStand.class, box)) {
      for (EquipmentSlot slot : MARKER_SLOTS) {
        ItemStack stack = armorStand.getItemBySlot(slot);
        if (stack == null || stack.isEmpty()) {
          continue;
        }
        markers.add(MarkerInfo.from(client.player, vehicle, armorStand, slot, stack));
      }
    }

    markers.sort(Comparator.comparingDouble(MarkerInfo::distanceToVehicle));
    return markers.size() <= MAX_MARKERS_PER_SAMPLE
        ? markers
        : new ArrayList<>(markers.subList(0, MAX_MARKERS_PER_SAMPLE));
  }

  public static JSONArray markersToJson(List<MarkerInfo> markers) {
    JSONArray out = new JSONArray();
    if (markers == null) {
      return out;
    }
    markers.stream().map(MarkerInfo::toJson).forEach(out::put);
    return out;
  }

  public static RideIdentity identifyRide(List<MarkerInfo> markers) {
    String markerKey = choosePrimaryMarkerKey(markers);
    if (markers != null) {
      for (MarkerInfo marker : markers) {
        if (isTronLightcycleMarker(marker)) {
          return new RideIdentity(
              TRON_LIGHTCYCLE_KEY,
              TRON_LIGHTCYCLE_NAME,
              markerKey,
              marker.damage,
              marker.damage == 122 ? "classic" : "alternate",
              rideEvidence(marker, "nearby_tron_lightcycle_model_marker"));
        }
      }
    }
    return new RideIdentity(
        UNKNOWN_RIDE_KEY,
        "",
        markerKey,
        -1,
        "",
        new JSONObject().put("reason", "no_known_marker"));
  }

  public static boolean isTronLightcycle(RideIdentity identity) {
    return identity != null && TRON_LIGHTCYCLE_KEY.equals(identity.rideKey());
  }

  private static String choosePrimaryMarkerKey(List<MarkerInfo> markers) {
    if (markers == null || markers.isEmpty()) {
      return UNKNOWN_RIDE_KEY;
    }
    MarkerInfo first = markers.get(0);
    return first.item().replace("minecraft:", "") + "_" + first.damage();
  }

  private static boolean isTronLightcycleMarker(MarkerInfo marker) {
    if (!"minecraft:diamond_pickaxe".equals(marker.item())) {
      return false;
    }
    if (!"head".equalsIgnoreCase(marker.slot())) {
      return false;
    }
    if (marker.damage() != 122 && marker.damage() != 377) {
      return false;
    }
    return marker.distanceToPlayer() <= TRON_MARKER_MAX_PLAYER_DISTANCE;
  }

  private static JSONObject rideEvidence(MarkerInfo marker, String reason) {
    return new JSONObject()
        .put("reason", reason)
        .put("markerEntityId", marker.entityId())
        .put("slot", marker.slot())
        .put("item", marker.item())
        .put("damage", marker.damage())
        .put("distanceToPlayer", marker.distanceToPlayer())
        .put("distanceToVehicle", marker.distanceToVehicle())
        .put("vehicleVariant", marker.damage() == 122 ? "classic" : "alternate");
  }

  private static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }

  private static String stripLegacyFormatting(String value) {
    return value == null ? "" : value.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
  }

  public record RideIdentity(
      String rideKey,
      String rideName,
      String primaryMarkerKey,
      int markerDamage,
      String vehicleVariant,
      JSONObject evidence) {}

  public record MarkerInfo(
      int entityId,
      String slot,
      String item,
      String name,
      int count,
      int damage,
      double distanceToVehicle,
      double distanceToPlayer,
      double x,
      double y,
      double z) {
    static MarkerInfo from(
        Entity player, Entity vehicle, ArmorStand armorStand, EquipmentSlot slot, ItemStack stack) {
      int damage = 0;
      try {
        damage = stack.getDamageValue();
      } catch (RuntimeException ignored) {
        // Keep profiling even if future stacks expose no damage value.
      }
      return new MarkerInfo(
          armorStand.getId(),
          slot.toString(),
          stack.getItem().toString(),
          stripLegacyFormatting(stack.getHoverName().getString()),
          stack.getCount(),
          damage,
          round3(armorStand.distanceTo(vehicle)),
          player == null ? -1.0 : round3(armorStand.distanceTo(player)),
          round3(armorStand.getX()),
          round3(armorStand.getY()),
          round3(armorStand.getZ()));
    }

    JSONObject toJson() {
      return new JSONObject()
          .put("entityId", entityId)
          .put("slot", slot)
          .put("item", item)
          .put("name", name)
          .put("count", count)
          .put("damage", damage)
          .put("distance", distanceToVehicle)
          .put("distanceToVehicle", distanceToVehicle)
          .put("distanceToPlayer", distanceToPlayer)
          .put("x", x)
          .put("y", y)
          .put("z", z);
    }
  }
}
