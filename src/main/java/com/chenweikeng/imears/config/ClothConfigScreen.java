package com.chenweikeng.imears.config;

import com.chenweikeng.imears.HideCrosshairMode;
import com.chenweikeng.imears.ImearsConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClothConfigScreen {
  private ClothConfigScreen() {}

  public static Screen createScreen(Screen parent) {
    ImearsConfig.Settings draft = ImearsConfig.current().copy();

    ConfigBuilder builder =
        ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Imaginears Helper"))
            .setSavingRunnable(() -> ImearsConfig.replace(draft));

    ConfigEntryBuilder entryBuilder = builder.entryBuilder();
    addHudCategory(builder, entryBuilder, draft);

    return builder.build();
  }

  private static void addHudCategory(
      ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ImearsConfig.Settings draft) {
    ConfigCategory hud = builder.getOrCreateCategory(Component.literal("HUD Visibility"));

    hud.addEntry(
        entryBuilder
            .startBooleanToggle(Component.literal("Hide scoreboard"), draft.hideScoreboard)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Hide the right-side scoreboard on Imaginears."))
            .setSaveConsumer(newValue -> draft.hideScoreboard = newValue)
            .build());

    hud.addEntry(
        entryBuilder
            .startBooleanToggle(Component.literal("Hide chat"), draft.hideChat)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Hide chat messages from the HUD."))
            .setSaveConsumer(newValue -> draft.hideChat = newValue)
            .build());

    hud.addEntry(
        entryBuilder
            .startBooleanToggle(Component.literal("Hide health"), draft.hideHealth)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Hide player hearts and vehicle hearts."))
            .setSaveConsumer(newValue -> draft.hideHealth = newValue)
            .build());

    hud.addEntry(
        entryBuilder
            .startBooleanToggle(Component.literal("Hide name tags"), draft.hideNameTags)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Hide player and entity name tags."))
            .setSaveConsumer(newValue -> draft.hideNameTags = newValue)
            .build());

    hud.addEntry(
        entryBuilder
            .startBooleanToggle(Component.literal("Hide hotbar"), draft.hideHotbar)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Hide the item hotbar."))
            .setSaveConsumer(newValue -> draft.hideHotbar = newValue)
            .build());

    hud.addEntry(
        entryBuilder
            .startBooleanToggle(Component.literal("Hide XP level"), draft.hideExperienceLevel)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Hide the green experience level number."))
            .setSaveConsumer(newValue -> draft.hideExperienceLevel = newValue)
            .build());

    hud.addEntry(
        entryBuilder
            .startEnumSelector(
                Component.literal("Hide crosshair"), HideCrosshairMode.class, draft.hideCrosshairMode)
            .setDefaultValue(HideCrosshairMode.NONE)
            .setTooltip(Component.literal("Choose when the center crosshair is hidden."))
            .setEnumNameProvider(mode -> crosshairModeName((HideCrosshairMode) mode))
            .setSaveConsumer(newValue -> draft.hideCrosshairMode = newValue)
            .build());
  }

  private static Component crosshairModeName(HideCrosshairMode mode) {
    return switch (mode) {
      case NONE -> Component.literal("None");
      case ONLY_WHEN_RIDING -> Component.literal("Only when riding");
      case ALWAYS -> Component.literal("Always");
    };
  }
}
