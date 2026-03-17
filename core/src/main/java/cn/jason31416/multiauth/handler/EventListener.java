package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.api.Profile;
import cn.jason31416.multiauth.message.Message;
import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.util.Logger;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
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
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Message.getMessage("auth.invalid-username").toRawComponent()));
                return;
            }

            UUID uuid = event.getUniqueId();
            if (uuid == null) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Message.getMessage("auth.failed-to-login").toRawComponent()));
                return;
            }

            LoginSession session = new LoginSession(username, uuid);
            LoginSession.getSessionMap().put(username, session);
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());

            if (Config.getBoolean("log.pre-login"))
                Logger.info("Player " + username + " (" + uuid + ") joined the server.");
        } catch (Throwable e) {
            Logger.error("Error when prelogin: " + e.getMessage());
            e.printStackTrace();
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Message.getMessage("auth.failed-to-login").toRawComponent()));
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
            Logger.warn("Failed to find login session for " + event.getUsername() + ".");
            return;
        }

        UUID authenticatedUuid = event.getOriginalProfile().getId();
        if (authenticatedUuid != null) {
            // Online / Yggdrasil-authenticated player.
            String yggdrasilName = event.getOriginalProfile().getName();
            String authMethod = session.getAuthMethod();
            if (authMethod == null) {
                Logger.warn("Failed to find authentication method for " + yggdrasilName + " (" + authenticatedUuid + ").");
                return;
            }

            Profile profile;
            try {
                profile = DatabaseHandler.getInstance().getOrCreateProfileForLogin(authMethod, authenticatedUuid, yggdrasilName);
            } catch (Exception e) {
                Logger.warn("Failed to get/create profile for " + yggdrasilName + " (" + authMethod + "): " + e.getMessage());
                event.setGameProfile(new GameProfile(authenticatedUuid, yggdrasilName, event.getOriginalProfile().getProperties()));
                return;
            }

            String finalProfileName = profile.name;
            try {
                Integer originalProfileId = DatabaseHandler.getInstance().getOriginalProfileId(authMethod, authenticatedUuid);
                if (originalProfileId != null
                        && originalProfileId == profile.id
                        && !yggdrasilName.equals(profile.name)) {
                    DatabaseHandler.getInstance().setProfileName(profile.id, yggdrasilName);
                    finalProfileName = yggdrasilName;
                }
            } catch (Exception e) {
                Logger.warn("Failed to sync profile name for " + yggdrasilName + " (" + authMethod + "): " + e.getMessage());
            }

            event.setGameProfile(new GameProfile(profile.uuid, finalProfileName, event.getOriginalProfile().getProperties()));
            return;
        }

        // Offline players.
        UUID offlineUuid = UuidUtils.generateOfflinePlayerUuid(event.getUsername());
        Profile offlineProfile;
        try {
            offlineProfile = DatabaseHandler.getInstance().getOrCreateProfileForLogin("offline", offlineUuid, event.getUsername());
        } catch (Exception e) {
            Logger.warn("Failed to get/create profile for offline player " + event.getUsername() + " (offline): " + e.getMessage());
            event.setGameProfile(event.getOriginalProfile().withId(offlineUuid));
            return;
        }
        event.setGameProfile(event.getOriginalProfile().withId(offlineProfile.uuid));
        Logger.warn("Used offline UUID " + offlineProfile.uuid + " for player " + event.getUsername() + ".");
    }
}
