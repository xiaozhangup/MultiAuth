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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class YggdrasilAuthenticator {

    public static CompletableFuture<PlayerProfile> authenticateVia(String username, String authMethod, String url){
        CompletableFuture<PlayerProfile> future = new CompletableFuture<>();
        var res = HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        res.thenAccept(response -> {
            if(response.statusCode() == 200) {
                var ret = new Gson().fromJson(response.body(), PlayerProfile.class);
                ret.authentication = authMethod;
                DatabaseHandler.getInstance().setPreferred(username, authMethod);
                LoginSession.getSession(username).setAuthMethod(authMethod);
                future.complete(ret);
            }else{
                future.complete(null);
            }
        });
        res.exceptionally(e -> {
            future.complete(null);
            return null;
        });
        return future;
    }

    private static String buildHasJoinedUrl(String baseUrl, String username, String serverID, String ip) {
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
        try{
            String preferredMethod = DatabaseHandler.getInstance().getPreferredMethod(username);
            if(preferredMethod!=null&&!preferredMethod.isEmpty()&&authServers.contains(preferredMethod)) {
                String url = buildHasJoinedUrl(authServers.get(preferredMethod), username, serverID, ip);
                var res = authenticateVia(username, preferredMethod, url);
                PlayerProfile preferred = res.get(10, TimeUnit.SECONDS);
                if (preferred != null){
                    return preferred;
                }
            }
            List<CompletableFuture<PlayerProfile>> futures = new ArrayList<>();
            for (String method : authServers.getKeys()) {
                if (!method.equals(preferredMethod)) {
                    String url1 = buildHasJoinedUrl(authServers.get(method), username, serverID, ip);
                    futures.add(authenticateVia(username, method, url1));
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .completeOnTimeout(null, 10, TimeUnit.SECONDS)
                    .join();

            for (CompletableFuture<PlayerProfile> future : futures) {
                if (future.isDone()) {
                    PlayerProfile result = future.get();
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }catch (Exception e){
            Logger.error("Failed to authenticate " + username + ": " + e.getMessage());
        }
        return null;
    }
}
