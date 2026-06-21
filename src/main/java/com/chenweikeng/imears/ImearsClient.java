package com.chenweikeng.imears;

import com.chenweikeng.imears.audio.OpenAudioMcService;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImearsClient implements ClientModInitializer {
  public static final String MOD_ID = "imears";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitializeClient() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) -> {
          registerOaCommand(dispatcher);
          registerRideProfileCommand(dispatcher);
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

  private static void registerRideProfileCommand(
      CommandDispatcher<FabricClientCommandSource> dispatcher) {
    dispatcher.register(
        ClientCommands.literal("rideprofile")
            .then(
                ClientCommands.literal("status")
                    .executes(
                        context -> {
                          RideProfiler.reportStatus(context.getSource().getClient());
                          return 1;
                        }))
            .then(
                ClientCommands.literal("flush")
                    .executes(
                        context -> {
                          RideProfiler.flush(context.getSource().getClient());
                          return 1;
                        })));
  }
}
