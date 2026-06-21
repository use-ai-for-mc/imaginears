package com.chenweikeng.imears.mixin;

import com.chenweikeng.imears.ServerState;
import com.chenweikeng.imears.VehicleRideController;
import com.chenweikeng.imears.audio.OpenAudioMcService;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ImearsMinecraftMixin {
  private static String lastServerIp;
  private static boolean wasOnImaginearsServer;

  @Inject(method = "tick", at = @At("TAIL"))
  private void imears$onClientTick(CallbackInfo ci) {
    Minecraft client = (Minecraft) (Object) this;

    VehicleRideController.tick(client);

    boolean currentlyImaginears = ServerState.isImaginearsServer(client);
    String currentServerIp =
        client.getCurrentServer() != null && client.getCurrentServer().ip != null
            ? client.getCurrentServer().ip.toLowerCase(Locale.ROOT)
            : null;

    OpenAudioMcService service = OpenAudioMcService.getInstance();

    if (!currentlyImaginears) {
      if (wasOnImaginearsServer) {
        service.disconnect();
      }
    } else if (!wasOnImaginearsServer
        || (lastServerIp != null && !lastServerIp.equals(currentServerIp))) {
      service.autoConnectOnJoin();
    }

    lastServerIp = currentServerIp;
    wasOnImaginearsServer = currentlyImaginears;
  }
}
