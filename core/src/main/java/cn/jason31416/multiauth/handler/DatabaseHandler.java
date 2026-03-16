package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.api.IDatabaseHandler;
import cn.jason31416.multiauth.api.Profile;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public class DatabaseHandler implements IDatabaseHandler {
    @Getter
    private static final DatabaseHandler instance=new DatabaseHandler();

    public static final String TABLE_AUTH_METHODS = "multiauth_authmethods";
    public static final String TABLE_PROFILES = "multiauth_profiles";
    public static final String TABLE_LOGIN_PROFILE = "multiauth_login_profile";

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
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + TABLE_PROFILES + " (id INT NOT NULL AUTO_INCREMENT, uuid VARCHAR(255) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, PRIMARY KEY (id))").execute();
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + TABLE_LOGIN_PROFILE + " (auth_method VARCHAR(255) NOT NULL, login_uuid VARCHAR(255) NOT NULL, profile_id INT NOT NULL, PRIMARY KEY (auth_method, login_uuid))").execute();
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

    @Override
    public int createProfile(UUID uuid, String name) throws SQLException {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "INSERT INTO " + TABLE_PROFILES + " (uuid, name) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.execute();
            var rs = st.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new SQLException("Failed to retrieve generated profile ID");
        }
    }

    @SneakyThrows
    @Override
    @Nullable
    public Profile getProfileById(int id) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "SELECT id, uuid, name FROM " + TABLE_PROFILES + " WHERE id = ?");
            st.setInt(1, id);
            var rs = st.executeQuery();
            if (rs.next()) {
                return new Profile(rs.getInt("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name"));
            }
            return null;
        }
    }

    @SneakyThrows
    @Override
    @Nullable
    public Profile getProfileByLogin(String authMethod, UUID loginUuid) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "SELECT p.id, p.uuid, p.name FROM " + TABLE_PROFILES + " p " +
                    "INNER JOIN " + TABLE_LOGIN_PROFILE + " lp ON p.id = lp.profile_id " +
                    "WHERE lp.auth_method = ? AND lp.login_uuid = ?");
            st.setString(1, authMethod);
            st.setString(2, loginUuid.toString());
            var rs = st.executeQuery();
            if (rs.next()) {
                return new Profile(rs.getInt("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name"));
            }
            return null;
        }
    }

    @SneakyThrows
    @Override
    public Profile getOrCreateProfileForLogin(String authMethod, UUID loginUuid, String name) {
        Profile existing = getProfileByLogin(authMethod, loginUuid);
        if (existing != null) {
            return existing;
        }
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                var st = connection.prepareStatement(
                        "INSERT INTO " + TABLE_PROFILES + " (uuid, name) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                st.setString(1, loginUuid.toString());
                st.setString(2, name);
                st.execute();
                var rs = st.getGeneratedKeys();
                if (!rs.next()) {
                    throw new SQLException("Failed to retrieve generated profile ID");
                }
                int profileId = rs.getInt(1);

                var st2 = connection.prepareStatement(
                        "INSERT INTO " + TABLE_LOGIN_PROFILE + " (auth_method, login_uuid, profile_id) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE profile_id = ?");
                st2.setString(1, authMethod);
                st2.setString(2, loginUuid.toString());
                st2.setInt(3, profileId);
                st2.setInt(4, profileId);
                st2.execute();

                connection.commit();
                return new Profile(profileId, loginUuid, name);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @SneakyThrows
    @Override
    public void setLoginProfile(String authMethod, UUID loginUuid, int profileId) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "INSERT INTO " + TABLE_LOGIN_PROFILE + " (auth_method, login_uuid, profile_id) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE profile_id = ?");
            st.setString(1, authMethod);
            st.setString(2, loginUuid.toString());
            st.setInt(3, profileId);
            st.setInt(4, profileId);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public void setProfileName(int id, String name) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement(
                    "UPDATE " + TABLE_PROFILES + " SET name = ? WHERE id = ?");
            st.setString(1, name);
            st.setInt(2, id);
            st.execute();
        }
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
