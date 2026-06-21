package com.chenweikeng.imears.mixin;

import com.chenweikeng.imears.ServerState;
import com.chenweikeng.imears.RideProfiler;
import com.chenweikeng.imears.audio.OpenAudioMcService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public class OpenAudioMcChatListenerMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger("imears/openaudiomc-chat");

  @Inject(method = "handleSystemMessage", at = @At("HEAD"))
  private void imears$onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
    if (message == null || !ServerState.isImaginearsServer(Minecraft.getInstance())) {
      return;
    }

    RideProfiler.recordChatMessage(message, overlay);

    String messageText = OpenAudioMcService.stripLegacyFormatting(message.getString());
    OpenAudioMcService service = OpenAudioMcService.getInstance();

    if (messageText.equals("You are now connected with the audio client!")) {
      service.onServerConfirmedConnection();
      return;
    }

    if (messageText.equals("You are already connected to the web client")) {
      service.onServerAlreadyConnected();
      return;
    }

    if (messageText.equals("Your audio session has been ended")) {
      service.onServerEndedSession();
      return;
    }

    String sessionUrl = OpenAudioMcService.extractSessionUrl(message);
    if (sessionUrl != null) {
      LOGGER.info("Detected OpenAudioMC session URL in chat");
      service.connectAsync(sessionUrl);
    } else if (messageText.contains("open the Web Client")) {
      LOGGER.warn("OpenAudioMC web-client prompt did not expose an OpenUrl ClickEvent");
    }
  }
}
