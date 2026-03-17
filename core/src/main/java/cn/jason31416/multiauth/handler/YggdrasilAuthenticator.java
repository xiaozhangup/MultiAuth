package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.util.Logger;
import cn.jason31416.multiauth.util.MapTree;
import cn.jason31416.multiauth.util.PlayerProfile;
import com.google.gson.Gson;
import lombok.SneakyThrows;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class YggdrasilAuthenticator {

    private static PlayerProfile authenticateVia(String username, String authMethod, String url) {
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                PlayerProfile ret = new Gson().fromJson(response.body(), PlayerProfile.class);
                ret.authentication = authMethod;
                DatabaseHandler.getInstance().setPreferred(username, authMethod);
                LoginSession.getSession(username).setAuthMethod(authMethod);
                return ret;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String buildAuthenticationUrl(String baseUrl, String username, String serverID, String ip) {
        String encoded = baseUrl + "session/minecraft/hasJoined?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId="
                + URLEncoder.encode(serverID, StandardCharsets.UTF_8);
        if (Config.getBoolean("authentication.yggdrasil.verify-ip")) {
            encoded += "&ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8);
        }
        return encoded;
    }

    @SneakyThrows
    public static PlayerProfile authenticate(String username, String serverID, String ip) {
        if (Config.getBoolean("log.auth-yggdrasil")) {
            Logger.info("Authenticating " + username);
        }
        MapTree authServers = Config.getSection("authentication.yggdrasil.auth-servers");
        try {
            String preferredMethod = DatabaseHandler.getInstance().getPreferredMethod(username);
            if (preferredMethod != null && !preferredMethod.isEmpty() && authServers.contains(preferredMethod)) {
                String url = buildAuthenticationUrl(authServers.get(preferredMethod), username, serverID, ip);
                PlayerProfile preferred = authenticateVia(username, preferredMethod, url);
                if (preferred != null) {
                    return preferred;
                }
            }

            // Fire concurrent requests for all remaining servers via virtual threads
            List<PlayerProfile[]> slots = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (String method : authServers.getKeys()) {
                if (!method.equals(preferredMethod)) {
                    String url1 = buildAuthenticationUrl(authServers.get(method), username, serverID, ip);
                    PlayerProfile[] slot = {null};
                    slots.add(slot);
                    threads.add(Thread.ofVirtual().start(() -> slot[0] = authenticateVia(username, method, url1)));
                }
            }

            long deadlineMs = System.currentTimeMillis() + 10_000;
            for (Thread t : threads) {
                long remaining = deadlineMs - System.currentTimeMillis();
                if (remaining > 0) t.join(Duration.ofMillis(remaining));
            }

            for (PlayerProfile[] slot : slots) {
                if (slot[0] != null) return slot[0];
            }
            return null;
        } catch (Exception e) {
            Logger.error("Failed to authenticate " + username + ": " + e.getMessage());
        }
        return null;
    }
}

