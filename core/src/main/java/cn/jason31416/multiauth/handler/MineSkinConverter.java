package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.util.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.util.GameProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

public final class MineSkinConverter {
    private static final URI QUEUE_URI = URI.create("https://api.mineskin.org/v2/queue");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private MineSkinConverter() {
    }

    // ponytail: one global conversion at a time; use a keyed queue if uncached login throughput matters.
    public static synchronized List<GameProfile.Property> convert(
            String authMethod, List<GameProfile.Property> properties
    ) {
        String apiKey = Config.getString("skin.mineskin.api-key").trim();
        if (apiKey.isEmpty() || "mojang".equalsIgnoreCase(authMethod)) return properties;

        for (int i = 0; i < properties.size(); i++) {
            GameProfile.Property property = properties.get(i);
            if (!"textures".equals(property.getName())) continue;

            try {
                SourceSkin source = parseSource(property.getValue());
                if (source == null) return properties;

                String sourceHash = sha256(source.url() + "\0" + source.variant());
                SignedTexture signed = findCached(sourceHash);
                if (signed == null) {
                    signed = generate(apiKey, source);
                    saveCached(sourceHash, signed);
                }

                List<GameProfile.Property> converted = new ArrayList<>(properties);
                converted.set(i, new GameProfile.Property("textures", signed.value(), signed.signature()));
                return converted;
            } catch (Exception e) {
                Logger.warn("MineSkin conversion failed for " + authMethod + ": " + e.getMessage());
                return properties;
            }
        }
        return properties;
    }

    static SourceSkin parseSource(String value) {
        JsonObject payload = JsonParser.parseString(new String(
                Base64.getDecoder().decode(value), StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonObject textures = payload.getAsJsonObject("textures");
        JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) return null;

        String variant = "classic";
        JsonObject metadata = skin.getAsJsonObject("metadata");
        if (metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString())) {
            variant = "slim";
        }
        return new SourceSkin(skin.get("url").getAsString(), variant);
    }

    static SignedTexture parseSignedTexture(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject skin = root.getAsJsonObject("skin");
        if (skin == null) return null;
        JsonObject data = skin.getAsJsonObject("texture").getAsJsonObject("data");
        if (data == null || !data.has("value") || !data.has("signature")) return null;

        SignedTexture result = new SignedTexture(
                data.get("value").getAsString(), data.get("signature").getAsString()
        );
        SourceSkin signedSkin = parseSource(result.value());
        if (signedSkin == null || !"textures.minecraft.net".equals(URI.create(signedSkin.url()).getHost())) {
            throw new IllegalArgumentException("MineSkin returned a non-Mojang texture URL");
        }
        return result;
    }

    private static SignedTexture generate(String apiKey, SourceSkin source) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("url", source.url());
        requestBody.addProperty("variant", source.variant());
        requestBody.addProperty("visibility", "unlisted");

        HttpResponse<String> response = send(HttpRequest.newBuilder(QUEUE_URI)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey));

        SignedTexture result = parseSignedTexture(response.body());
        if (result != null) return result;

        JsonObject job = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("job");
        if (job == null || !job.has("id")) throw new IllegalStateException("MineSkin returned no job ID");

        String jobId = job.get("id").getAsString();
        long deadline = System.nanoTime() + JOB_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.sleep(1000);
            response = send(HttpRequest.newBuilder(URI.create(QUEUE_URI + "/" + jobId))
                    .GET()
                    .header("Authorization", "Bearer " + apiKey));
            result = parseSignedTexture(response.body());
            if (result != null) return result;

            job = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("job");
            if (job != null && job.has("status") && "failed".equals(job.get("status").getAsString())) {
                throw new IllegalStateException("MineSkin job failed");
            }
        }
        throw new IllegalStateException("MineSkin job timed out");
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = HTTP.send(request
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "MultiAuth/2.2.2")
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 202) {
            throw new IllegalStateException("MineSkin HTTP " + response.statusCode());
        }
        return response;
    }

    private static SignedTexture findCached(String sourceHash) throws Exception {
        try (
                Connection connection = DatabaseHandler.getInstance().getConnection();
                var st = connection.prepareStatement("SELECT value, signature FROM %s WHERE source_hash = ?"
                        .formatted(DatabaseHandler.TABLE_SKIN_CACHE))
        ) {
            st.setString(1, sourceHash);
            try (var rs = st.executeQuery()) {
                return rs.next() ? new SignedTexture(rs.getString("value"), rs.getString("signature")) : null;
            }
        }
    }

    private static void saveCached(String sourceHash, SignedTexture texture) throws Exception {
        try (
                Connection connection = DatabaseHandler.getInstance().getConnection();
                var st = connection.prepareStatement("""
                        INSERT INTO %s (source_hash, value, signature) VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE value = ?, signature = ?
                        """.formatted(DatabaseHandler.TABLE_SKIN_CACHE))
        ) {
            st.setString(1, sourceHash);
            st.setString(2, texture.value());
            st.setString(3, texture.signature());
            st.setString(4, texture.value());
            st.setString(5, texture.signature());
            st.execute();
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    record SourceSkin(String url, String variant) {
    }

    record SignedTexture(String value, String signature) {
    }
}
