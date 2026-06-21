package com.chenweikeng.imears;

public enum HideCrosshairMode {
  NONE,
  ONLY_WHEN_RIDING,
  ALWAYS;

  public static HideCrosshairMode fromCommandValue(String value) {
    return switch (value.toLowerCase()) {
      case "none" -> NONE;
      case "riding", "only_when_riding", "only-when-riding" -> ONLY_WHEN_RIDING;
      case "always" -> ALWAYS;
      default -> throw new IllegalArgumentException("Unknown crosshair mode: " + value);
    };
  }

  public String commandValue() {
    return switch (this) {
      case NONE -> "none";
      case ONLY_WHEN_RIDING -> "riding";
      case ALWAYS -> "always";
    };
  }
}
