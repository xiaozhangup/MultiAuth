package cn.jason31416.authX.handler;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import lombok.Getter;

public class ModProtocolHandler {
    @Getter
    private static final String hashsalt = "totallysecureenoughqwq";
    @Getter
    private static final MinecraftChannelIdentifier channelIdentifier = MinecraftChannelIdentifier.create("authx", "mod/authx");
}
