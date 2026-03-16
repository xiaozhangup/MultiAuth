package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.MultiAuth;
import cn.jason31416.multiauth.hook.FloodgateHandler;
import cn.jason31416.multiauth.message.Message;
import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.util.Logger;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import java.util.UUID;

public class EventListener {

    @SneakyThrows
    @Subscribe
    public void onPreLogin(@Nonnull PreLoginEvent event) {
        try {
            String username = event.getUsername();

            if (!username.matches(Config.getConfigTree().getString("regex.username-regex", ".*"))) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Message.getMessage("auth.invalid-username").toComponent()));
                return;
            }

            UUID uuid = event.getUniqueId();
            if (uuid == null) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Message.getMessage("auth.failed-to-login").toComponent()));
                return;
            }

            LoginSession session = new LoginSession(username, uuid);
            LoginSession.getSessionMap().put(username, session);

            if (MultiAuth.getInstance().getProxy().getPluginManager().isLoaded("floodgate") && FloodgateHandler.isFloodgatePlayer(uuid)) {
                return;
            }

            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());

            if (Config.getBoolean("log.pre-login"))
                Logger.info("Player " + username + " (" + uuid + ") joined the server.");
        } catch (Throwable e) {
            Logger.error("Error when prelogin: " + e.getMessage());
            e.printStackTrace();
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Message.getMessage("auth.failed-to-login").toComponent()));
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        LoginSession.getSessionMap().remove(event.getPlayer().getUsername());
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        LoginSession session = LoginSession.getSessionMap().get(event.getUsername());
        if (session == null) {
            return;
        }

        UUID authenticatedUuid = event.getOriginalProfile().getId();
        if (authenticatedUuid != null) {
            try {
                DatabaseHandler.getInstance().setUUID(event.getUsername(), authenticatedUuid);
            } catch (Exception e) {
                Logger.warn("Failed to persist authenticated UUID for " + event.getUsername() + ": " + e.getMessage());
            }
            event.setGameProfile(event.getOriginalProfile().withId(authenticatedUuid));
            return;
        }

        GameProfile profile = event.getOriginalProfile().withId(DatabaseHandler.getInstance().getUUID(event.getUsername()));
        event.setGameProfile(profile);
    }
}
