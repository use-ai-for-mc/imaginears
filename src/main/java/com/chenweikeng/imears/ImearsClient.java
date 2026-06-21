package com.chenweikeng.imears;

import com.chenweikeng.imears.audio.OpenAudioMcService;
import com.chenweikeng.imears.config.ClothConfigScreen;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImearsClient implements ClientModInitializer {
  public static final String MOD_ID = "imears";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final String[] HUD_ELEMENTS = {
    "scoreboard", "chat", "health", "nametags", "hotbar", "xp"
  };
  private static final String[] CROSSHAIR_MODES = {"none", "riding", "always"};
  private static final SuggestionProvider<FabricClientCommandSource> HUD_ELEMENT_SUGGESTIONS =
      (context, builder) -> SharedSuggestionProvider.suggest(HUD_ELEMENTS, builder);
  private static final SuggestionProvider<FabricClientCommandSource> CROSSHAIR_MODE_SUGGESTIONS =
      (context, builder) -> SharedSuggestionProvider.suggest(CROSSHAIR_MODES, builder);

  @Override
  public void onInitializeClient() {
    ImearsConfig.load();

    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) -> {
          registerOaCommand(dispatcher);
          registerTronCommand(dispatcher);
          registerImearsCommand(dispatcher);
        });

    LOGGER.info("Imaginears Helper initialized");
  }

  private static void registerOaCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
    dispatcher.register(
        ClientCommands.literal("oa")
            .then(
                ClientCommands.literal("connect")
                    .executes(
                        context -> {
                          OpenAudioMcService.getInstance().connectViaCommand();
                          return 1;
                        }))
            .then(
                ClientCommands.literal("disconnect")
                    .executes(
                        context -> {
                          OpenAudioMcService.getInstance().disconnectViaCommand();
                          return 1;
                        }))
            .then(
                ClientCommands.literal("reconnect")
                    .executes(
                        context -> {
                          OpenAudioMcService.getInstance().reconnectWithFallback();
                          return 1;
                        }))
            .then(
                ClientCommands.literal("volume")
                    .executes(
                        context -> {
                          OpenAudioMcService.getInstance().reportVolume();
                          return 1;
                        })));
  }

  private static void registerTronCommand(
      CommandDispatcher<FabricClientCommandSource> dispatcher) {
    dispatcher.register(
        ClientCommands.literal("tron")
            .then(
                ClientCommands.literal("status")
                    .executes(
                        context -> {
                          RideProgressTracker.reportStatus(context.getSource().getClient());
                          return Command.SINGLE_SUCCESS;
                        }))
            .then(
                ClientCommands.literal("reset")
                    .executes(
                        context -> {
                          RideProgressTracker.resetCommand(context.getSource().getClient());
                          return Command.SINGLE_SUCCESS;
                        })));
  }

  private static void registerImearsCommand(
      CommandDispatcher<FabricClientCommandSource> dispatcher) {
    dispatcher.register(
        ClientCommands.literal("imears")
            .then(
                ClientCommands.literal("config")
                    .executes(
                        context -> {
                          context
                              .getSource()
                              .getClient()
                              .setScreen(
                                  ClothConfigScreen.createScreen(
                                      context.getSource().getClient().screen));
                          return Command.SINGLE_SUCCESS;
                        }))
            .then(
                ClientCommands.literal("hud")
                    .then(
                        ClientCommands.literal("status")
                            .executes(
                                context -> {
                                  reportHudStatus(context.getSource());
                                  return Command.SINGLE_SUCCESS;
                                }))
                    .then(
                        ClientCommands.literal("reload")
                            .executes(
                                context -> {
                                  ImearsConfig.load();
                                  notify(context.getSource(), "Reloaded config.");
                                  reportHudStatus(context.getSource());
                                  return Command.SINGLE_SUCCESS;
                                }))
                    .then(
                        ClientCommands.literal("reset")
                            .executes(
                                context -> {
                                  ImearsConfig.reset();
                                  notify(context.getSource(), "Reset HUD config to defaults.");
                                  reportHudStatus(context.getSource());
                                  return Command.SINGLE_SUCCESS;
                                }))
                    .then(
                        ClientCommands.literal("hide")
                            .then(
                                ClientCommands.argument("element", StringArgumentType.word())
                                    .suggests(HUD_ELEMENT_SUGGESTIONS)
                                    .executes(
                                        context ->
                                            setHudElement(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "element"),
                                                true))))
                    .then(
                        ClientCommands.literal("show")
                            .then(
                                ClientCommands.argument("element", StringArgumentType.word())
                                    .suggests(HUD_ELEMENT_SUGGESTIONS)
                                    .executes(
                                        context ->
                                            setHudElement(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "element"),
                                                false))))
                    .then(
                        ClientCommands.literal("toggle")
                            .then(
                                ClientCommands.argument("element", StringArgumentType.word())
                                    .suggests(HUD_ELEMENT_SUGGESTIONS)
                                    .executes(
                                        context ->
                                            toggleHudElement(
                                                context.getSource(),
                                                StringArgumentType.getString(
                                                    context, "element")))))
                    .then(
                        ClientCommands.literal("crosshair")
                            .then(
                                ClientCommands.argument("mode", StringArgumentType.word())
                                    .suggests(CROSSHAIR_MODE_SUGGESTIONS)
                                    .executes(
                                        context ->
                                            setCrosshairMode(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "mode")))))));
  }

  private static int setHudElement(
      FabricClientCommandSource source, String element, boolean hidden) {
    try {
      String label = ImearsConfig.setHudElement(element, hidden);
      ImearsConfig.save();
      notify(source, (hidden ? "Hidden " : "Shown ") + label + ".");
      return Command.SINGLE_SUCCESS;
    } catch (IllegalArgumentException e) {
      notify(source, e.getMessage() + ". Options: " + String.join(", ", HUD_ELEMENTS));
      return 0;
    }
  }

  private static int toggleHudElement(FabricClientCommandSource source, String element) {
    try {
      String label = ImearsConfig.toggleHudElement(element);
      ImearsConfig.save();
      boolean hidden = ImearsConfig.current().isHidden(element);
      notify(source, label + " is now " + (hidden ? "hidden" : "shown") + ".");
      return Command.SINGLE_SUCCESS;
    } catch (IllegalArgumentException e) {
      notify(source, e.getMessage() + ". Options: " + String.join(", ", HUD_ELEMENTS));
      return 0;
    }
  }

  private static int setCrosshairMode(FabricClientCommandSource source, String modeValue) {
    try {
      HideCrosshairMode mode = HideCrosshairMode.fromCommandValue(modeValue);
      ImearsConfig.current().hideCrosshairMode = mode;
      ImearsConfig.save();
      notify(source, "Crosshair mode set to " + mode.commandValue() + ".");
      return Command.SINGLE_SUCCESS;
    } catch (IllegalArgumentException e) {
      notify(source, "Unknown crosshair mode. Options: " + String.join(", ", CROSSHAIR_MODES));
      return 0;
    }
  }

  private static void reportHudStatus(FabricClientCommandSource source) {
    ImearsConfig.Settings settings = ImearsConfig.current();
    notify(
        source,
        "HUD: "
            + "scoreboard="
            + visibility(settings.hideScoreboard)
            + ", chat="
            + visibility(settings.hideChat)
            + ", health="
            + visibility(settings.hideHealth)
            + ", nametags="
            + visibility(settings.hideNameTags)
            + ", hotbar="
            + visibility(settings.hideHotbar)
            + ", xp="
            + visibility(settings.hideExperienceLevel)
            + ", crosshair="
            + settings.hideCrosshairMode.commandValue()
            + ".");
    notify(source, "Config: " + ImearsConfig.path());
  }

  private static String visibility(boolean hidden) {
    return hidden ? "hidden" : "shown";
  }

  private static void notify(FabricClientCommandSource source, String message) {
    source
        .getClient()
        .gui
        .getChat()
        .addClientSystemMessage(Component.literal("\u00A76[IMEARS] \u00A7f" + message));
  }
}
