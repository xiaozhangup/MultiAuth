package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.api.IDatabaseHandler;
import com.velocitypowered.api.util.UuidUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DatabaseHandler implements IDatabaseHandler {
    @Getter
    private static final DatabaseHandler instance=new DatabaseHandler();

    public static final String TABLE_AUTH_METHODS = "multiauth_authmethods";
    public static final String TABLE_UUID_DATA = "multiauth_uuiddata";

    public HikariDataSource dataSource;

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @SneakyThrows
    public void init() {
        if(dataSource!= null&&!dataSource.isClosed()) dataSource.close();

        dataSource = new HikariDataSource(buildDataSourceConfig());

        try (Connection connection = getConnection()) {
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + TABLE_AUTH_METHODS + " (username VARCHAR(255) PRIMARY KEY, verified VARCHAR(255), preferred VARCHAR(255), modkey VARCHAR(255) default NULL)").execute();
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + TABLE_UUID_DATA + " (uuid VARCHAR(255) PRIMARY KEY, username VARCHAR(255))").execute();
        }
    }

    private HikariConfig buildDataSourceConfig() {
        HikariConfig config = new HikariConfig();
        String host = Config.getString("authentication.mysql.host");
        int port = Config.getInt("authentication.mysql.port");
        String database = Config.getString("authentication.mysql.database");
        String username = Config.getString("authentication.mysql.username");
        String password = Config.getString("authentication.mysql.password");
        String parameters = Config.getString("authentication.mysql.parameters");

        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(host.isEmpty() ? "localhost" : host)
                .append(":")
                .append(port <= 0 ? 3306 : port)
                .append("/")
                .append(database.isEmpty() ? "authx" : database);
        if (!parameters.isEmpty()) {
            url.append("?").append(parameters);
        }
        config.setJdbcUrl(url.toString());
        config.setUsername(username);
        config.setPassword(password);
        return config;
    }

    @SneakyThrows
    @Override
    public void setUUID(String username, UUID uuid){
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "INSERT INTO " + TABLE_UUID_DATA + " (uuid, username) VALUES (?,?) " +
                    "ON DUPLICATE KEY UPDATE username = ?");
            st.setString(1, uuid.toString());
            st.setString(2, username);
            st.setString(3, username);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public UUID getUUID(String username) {
        UUID result = getUUIDByUsername(username);
        return result != null ? result : UuidUtils.generateOfflinePlayerUuid(username);
    }

    @SneakyThrows
    @Override
    @Nullable
    public UUID getUUIDByUsername(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "SELECT uuid FROM " + TABLE_UUID_DATA + " WHERE username = ?");
            st.setString(1, username);
            var rs = st.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
            return null;
        }
    }

    @SneakyThrows
    @Override
    @Nullable
    public String getUsernameByUUID(UUID uuid) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "SELECT username FROM " + TABLE_UUID_DATA + " WHERE uuid = ?");
            st.setString(1, uuid.toString());
            var rs = st.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
            return null;
        }
    }

    @Override
    public String resolveAndPersistUUID(UUID uuid, String yggdrasilName) {
        String storedName = getUsernameByUUID(uuid);

        if (storedName != null && storedName.equals(yggdrasilName)) {
            // UUID already mapped to this exact username — nothing to do.
            return storedName;
        }

        // Either it's a new player, or the Yggdrasil name has changed.
        // Check whether the desired name is already occupied by a *different* UUID.
        UUID existingUuidForName = getUUIDByUsername(yggdrasilName);
        String effectiveName;
        if (existingUuidForName != null && !existingUuidForName.equals(uuid)) {
            if (storedName != null) {
                // Name change requested but new name is taken — keep the current stored name.
                effectiveName = storedName;
            } else {
                // New player whose desired name is taken — append suffix to avoid conflict.
                effectiveName = yggdrasilName + "1";
            }
        } else {
            // Name is available (or belongs to this UUID already).
            effectiveName = yggdrasilName;
        }

        setUUID(effectiveName, uuid);
        return effectiveName;
    }

    @SneakyThrows
    public void setModKey(String username, String modkey){
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("UPDATE " + TABLE_AUTH_METHODS + " SET modkey =? WHERE username =?");
            st.setString(1, modkey);
            st.setString(2, username);
            st.execute();
        }
    }
    @SneakyThrows @Nullable
    public String getModKey(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("SELECT modkey FROM " + TABLE_AUTH_METHODS + " WHERE username =?");
            st.setString(1, username);
            var rs = st.executeQuery();
            if (rs.next()) {
                return rs.getString("modkey");
            } else {
                return null;
            }
        }
    }

    @SneakyThrows
    @Override
    public void setPreferred(String username, String method){
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("INSERT INTO " + TABLE_AUTH_METHODS + " (username, verified, preferred) VALUES (?,?,?) ON DUPLICATE KEY UPDATE preferred = ?");
            st.setString(1, username);
            st.setString(2, "");
            st.setString(3, method);
            st.setString(4, method);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public void addAuthMethod(String username, String method) { // it is assumed that user is already created
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("UPDATE " + TABLE_AUTH_METHODS + " SET verified = CONCAT(COALESCE(verified, ''), ?) WHERE username =?");
            st.setString(1, "," + method);
            st.setString(2, username);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public List<String> getAuthMethods(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("SELECT verified FROM " + TABLE_AUTH_METHODS + " WHERE username =?");
            st.setString(1, username);
            var rs = st.executeQuery();
            if (rs.next()) {
                var verified = rs.getString("verified");
                if (verified == null || verified.isEmpty()) {
                    return List.of();
                } else {
                    return List.of(verified.substring(1).split(","));
                }
            } else {
                return List.of();
            }
        }
    }
    @SneakyThrows
    @Override
    public String getPreferredMethod(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("SELECT preferred FROM " + TABLE_AUTH_METHODS + " WHERE username =?");
            st.setString(1, username);
            var rs = st.executeQuery();
            if (rs.next()) {
                return rs.getString("preferred");
            } else {
                return null;
            }
        }
    }
}
