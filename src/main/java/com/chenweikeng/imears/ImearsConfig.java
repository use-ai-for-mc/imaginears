package com.chenweikeng.imears;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;

public final class ImearsConfig {
  private static final Path CONFIG_PATH =
      FabricLoader.getInstance().getConfigDir().resolve("imears").resolve("config.json");

  private static Settings current = new Settings();

  private ImearsConfig() {}

  public static Settings current() {
    return current;
  }

  public static Path path() {
    return CONFIG_PATH;
  }

  public static void load() {
    if (!Files.exists(CONFIG_PATH)) {
      current = new Settings();
      save();
      return;
    }

    try {
      current = Settings.fromJson(new JSONObject(Files.readString(CONFIG_PATH)));
    } catch (RuntimeException | IOException e) {
      ImearsClient.LOGGER.warn("Failed to load IMEARS config, using defaults", e);
      current = new Settings();
    }
  }

  public static void save() {
    try {
      Files.createDirectories(CONFIG_PATH.getParent());
      Files.writeString(CONFIG_PATH, current.toJson().toString(2) + System.lineSeparator());
    } catch (IOException e) {
      ImearsClient.LOGGER.error("Failed to save IMEARS config", e);
    }
  }

  public static void reset() {
    current = new Settings();
    save();
  }

  public static String setHudElement(String element, boolean hidden) {
    return current.setHudElement(element, hidden);
  }

  public static String toggleHudElement(String element) {
    return current.toggleHudElement(element);
  }

  public static final class Settings {
    public boolean hideScoreboard = false;
    public boolean hideChat = false;
    public boolean hideHealth = true;
    public boolean hideNameTags = false;
    public boolean hideHotbar = false;
    public boolean hideExperienceLevel = false;
    public HideCrosshairMode hideCrosshairMode = HideCrosshairMode.NONE;

    static Settings fromJson(JSONObject json) {
      Settings settings = new Settings();
      settings.hideScoreboard = json.optBoolean("hideScoreboard", settings.hideScoreboard);
      settings.hideChat = json.optBoolean("hideChat", settings.hideChat);
      settings.hideHealth = json.optBoolean("hideHealth", settings.hideHealth);
      settings.hideNameTags =
          json.optBoolean("hideNameTags", json.optBoolean("hideNameTag", settings.hideNameTags));
      settings.hideHotbar = json.optBoolean("hideHotbar", settings.hideHotbar);
      settings.hideExperienceLevel =
          json.optBoolean("hideExperienceLevel", settings.hideExperienceLevel);
      settings.hideCrosshairMode =
          parseCrosshairMode(json.optString("hideCrosshairMode", settings.hideCrosshairMode.name()));
      return settings;
    }

    JSONObject toJson() {
      return new JSONObject()
          .put("hideScoreboard", hideScoreboard)
          .put("hideChat", hideChat)
          .put("hideHealth", hideHealth)
          .put("hideNameTags", hideNameTags)
          .put("hideHotbar", hideHotbar)
          .put("hideExperienceLevel", hideExperienceLevel)
          .put("hideCrosshairMode", hideCrosshairMode.name());
    }

    String setHudElement(String element, boolean hidden) {
      return switch (normalizeElement(element)) {
        case "scoreboard" -> {
          hideScoreboard = hidden;
          yield "scoreboard";
        }
        case "chat" -> {
          hideChat = hidden;
          yield "chat";
        }
        case "health" -> {
          hideHealth = hidden;
          yield "health";
        }
        case "nametags" -> {
          hideNameTags = hidden;
          yield "name tags";
        }
        case "hotbar" -> {
          hideHotbar = hidden;
          yield "hotbar";
        }
        case "xp" -> {
          hideExperienceLevel = hidden;
          yield "experience level";
        }
        default -> throw new IllegalArgumentException("Unknown HUD element: " + element);
      };
    }

    String toggleHudElement(String element) {
      return switch (normalizeElement(element)) {
        case "scoreboard" -> {
          hideScoreboard = !hideScoreboard;
          yield "scoreboard";
        }
        case "chat" -> {
          hideChat = !hideChat;
          yield "chat";
        }
        case "health" -> {
          hideHealth = !hideHealth;
          yield "health";
        }
        case "nametags" -> {
          hideNameTags = !hideNameTags;
          yield "name tags";
        }
        case "hotbar" -> {
          hideHotbar = !hideHotbar;
          yield "hotbar";
        }
        case "xp" -> {
          hideExperienceLevel = !hideExperienceLevel;
          yield "experience level";
        }
        default -> throw new IllegalArgumentException("Unknown HUD element: " + element);
      };
    }

    boolean isHidden(String element) {
      return switch (normalizeElement(element)) {
        case "scoreboard" -> hideScoreboard;
        case "chat" -> hideChat;
        case "health" -> hideHealth;
        case "nametags" -> hideNameTags;
        case "hotbar" -> hideHotbar;
        case "xp" -> hideExperienceLevel;
        default -> false;
      };
    }

    private static HideCrosshairMode parseCrosshairMode(String value) {
      try {
        return HideCrosshairMode.valueOf(value.toUpperCase());
      } catch (IllegalArgumentException ignored) {
        try {
          return HideCrosshairMode.fromCommandValue(value);
        } catch (IllegalArgumentException ignoredAgain) {
          return HideCrosshairMode.NONE;
        }
      }
    }

    private static String normalizeElement(String element) {
      return element == null
          ? ""
          : switch (element.toLowerCase().replace("_", "-")) {
            case "scoreboard" -> "scoreboard";
            case "chat" -> "chat";
            case "health", "hearts" -> "health";
            case "name-tag", "name-tags", "nametag", "nametags" -> "nametags";
            case "hotbar" -> "hotbar";
            case "xp", "experience", "experience-level", "level" -> "xp";
            default -> element.toLowerCase();
          };
    }
  }
}
