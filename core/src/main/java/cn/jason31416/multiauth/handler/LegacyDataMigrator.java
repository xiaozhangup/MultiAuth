package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.api.Profile;
import lombok.Getter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.ByteBuffer;
import java.util.UUID;

public class LegacyDataMigrator {
    @Getter
    private static final LegacyDataMigrator instance = new LegacyDataMigrator();

    private static final String LEGACY_TABLE_NAME = "multilogin_user_data_v3";

    public MigrationResult migrate() throws Exception {
        MigrationResult result = new MigrationResult();
        String query = "SELECT online_name, online_uuid, service_id FROM `" + LEGACY_TABLE_NAME + "`";

        try (Connection connection = DatabaseHandler.getInstance().getConnection();
             PreparedStatement st = connection.prepareStatement(query);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.totalRows++;

                String username = rs.getString("online_name");
                byte[] onlineUuidBinary = rs.getBytes("online_uuid");
                int serviceId = rs.getInt("service_id");
                if (rs.wasNull()) {
                    result.skippedRows++;
                    continue;
                }

                if (username == null || username.isBlank() || onlineUuidBinary == null) {
                    result.skippedRows++;
                    continue;
                }

                String authMethod = mapServiceIdToAuthMethod(serviceId);
                if (authMethod == null) {
                    result.skippedRows++;
                    continue;
                }

                UUID loginUuid = parseLegacyUuid(onlineUuidBinary);
                if (loginUuid == null) {
                    result.skippedRows++;
                    continue;
                }

                try {
                    Profile profile = DatabaseHandler.getInstance().getOrCreateProfileForLogin(authMethod, loginUuid, username);
                    DatabaseHandler.getInstance().setPreferred(username, authMethod);
                    DatabaseHandler.getInstance().setLoginProfileWithOriginalId(authMethod, loginUuid, profile.id, profile.id);
                    result.migratedRows++;
                } catch (Exception e) {
                    result.failedRows++;
                }
            }
        }

        return result;
    }

    private String mapServiceIdToAuthMethod(int serviceId) {
        return switch (serviceId) {
            case 0 -> "mojang";
            case 1 -> "littleskin";
            default -> null;
        };
    }

    private UUID parseLegacyUuid(byte[] raw) {
        if (raw.length != 16) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        long most = buffer.getLong();
        long least = buffer.getLong();
        return new UUID(most, least);
    }

    public static class MigrationResult {
        public int totalRows;
        public int migratedRows;
        public int skippedRows;
        public int failedRows;
    }
}
