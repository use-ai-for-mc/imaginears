package com.chenweikeng.imears;

import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class ServerState {
  private ServerState() {}

  public static boolean isImaginearsServer(Minecraft client) {
    if (client == null || client.getCurrentServer() == null || client.getCurrentServer().ip == null) {
      return false;
    }

    String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":")[0];
    return host.equals("iears.us") || host.endsWith(".iears.us");
  }
}
