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
import java.util.UUID;

public class DatabaseHandler implements IDatabaseHandler {
    @Getter
    private static final DatabaseHandler instance = new DatabaseHandler();

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
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();

        dataSource = new HikariDataSource(buildDataSourceConfig());

        try (
                Connection connection = getConnection();
                Statement st = connection.createStatement()
        ) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        username VARCHAR(255) PRIMARY KEY,
                        preferred VARCHAR(255)
                    )""".formatted(TABLE_AUTH_METHODS));

            st.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id INT NOT NULL AUTO_INCREMENT,
                        uuid VARCHAR(36) NOT NULL UNIQUE,
                        name VARCHAR(255) NOT NULL,
                        PRIMARY KEY (id)
                    )""".formatted(TABLE_PROFILES));

            st.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        auth_method VARCHAR(255) NOT NULL,
                        login_uuid VARCHAR(36) NOT NULL,
                        profile_id INT NOT NULL,
                        original_profile_id INT NOT NULL,
                        PRIMARY KEY (auth_method, login_uuid)
                    )""".formatted(TABLE_LOGIN_PROFILE));
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
        String url = "jdbc:mysql://%s:%d/%s%s".formatted(
                host.isBlank() ? "localhost" : host,
                port <= 0 ? 3306 : port,
                database.isBlank() ? "multiauth" : database,
                parameters.isBlank() ? "" : "?" + parameters
        );

        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        return config;
    }

    @Override
    public int createProfile(UUID uuid, String name) throws SQLException {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement(
                        "INSERT INTO %s (uuid, name) VALUES (?, ?)".formatted(TABLE_PROFILES),
                        Statement.RETURN_GENERATED_KEYS
                )
        ) {
            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.execute();
            try (var rs = st.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new SQLException("Failed to retrieve generated profile ID");
        }
    }

    @SneakyThrows
    @Override
    @Nullable
    public Profile getProfileById(int id) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("SELECT id, uuid, name FROM %s WHERE id = ?".formatted(TABLE_PROFILES))
        ) {
            st.setInt(1, id);
            try (var rs = st.executeQuery()) {
                if (rs.next()) {
                    return new Profile(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name")
                    );
                }
                return null;
            }
        }
    }

    @SneakyThrows
    @Override
    @Nullable
    public Profile getProfileByLogin(String authMethod, UUID loginUuid) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("""
                        SELECT p.id, p.uuid, p.name
                        FROM %s p
                        INNER JOIN %s lp ON p.id = lp.profile_id
                        WHERE lp.auth_method = ? AND lp.login_uuid = ?
                        """.formatted(TABLE_PROFILES, TABLE_LOGIN_PROFILE))
        ) {
            st.setString(1, authMethod);
            st.setString(2, loginUuid.toString());
            try (var rs = st.executeQuery()) {
                if (rs.next()) {
                    return new Profile(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name")
                    );
                }
                return null;
            }
        }
    }

    @SneakyThrows
    @Override
    @Nullable
    public Profile getProfileByName(String name) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("SELECT id, uuid, name FROM %s WHERE name = ? LIMIT 1".formatted(TABLE_PROFILES))
        ) {
            st.setString(1, name);
            try (var rs = st.executeQuery()) {
                if (rs.next()) {
                    return new Profile(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name")
                    );
                }
                return null;
            }
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
                String resolvedName = resolveUniqueProfileName(connection, name);

                try (
                        var st = connection.prepareStatement(
                                "INSERT INTO %s (uuid, name) VALUES (?, ?)".formatted(TABLE_PROFILES),
                                Statement.RETURN_GENERATED_KEYS
                        )
                ) {
                    st.setString(1, loginUuid.toString());
                    st.setString(2, resolvedName);
                    st.execute();
                    try (var rs = st.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("Failed to retrieve generated profile ID");
                        }
                        int profileId = rs.getInt(1);

                        try (var st2 = connection.prepareStatement("""
                                INSERT INTO %s (auth_method, login_uuid, profile_id, original_profile_id)
                                VALUES (?, ?, ?, ?)
                                ON DUPLICATE KEY UPDATE profile_id = ?, original_profile_id = original_profile_id
                                """.formatted(TABLE_LOGIN_PROFILE))) {
                            st2.setString(1, authMethod);
                            st2.setString(2, loginUuid.toString());
                            st2.setInt(3, profileId);
                            st2.setInt(4, profileId);
                            st2.setInt(5, profileId);
                            st2.execute();
                        }

                        connection.commit();
                        return new Profile(profileId, loginUuid, resolvedName);
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                // Handle concurrent insert: if a duplicate key occurred, retry the lookup
                // so concurrent logins for the same player converge to the same profile.
                if (isDuplicateKeyException(e)) {
                    Profile retry = getProfileByLogin(authMethod, loginUuid);
                    if (retry != null) {
                        return retry;
                    }
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static boolean isDuplicateKeyException(SQLException e) {
        return e.getErrorCode() == 1062 || "23000".equals(e.getSQLState());
    }

    private String resolveUniqueProfileName(Connection connection, String baseName) throws SQLException {
        String candidate = baseName;
        int maxAttempts = 4;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try (var st = connection.prepareStatement("SELECT 1 FROM %s WHERE name = ? LIMIT 1".formatted(TABLE_PROFILES))) {
                st.setString(1, candidate);
                try (var rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return candidate;
                    }
                }
            }
            candidate = baseName + "_".repeat(attempt + 1);
        }
        return baseName + "_" + UUID.randomUUID().toString().substring(0, 3);
    }

    @SneakyThrows
    @Nullable
    public Integer getOriginalProfileId(String authMethod, UUID loginUuid) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("SELECT original_profile_id FROM %s WHERE auth_method = ? AND login_uuid = ?".formatted(TABLE_LOGIN_PROFILE))
        ) {
            st.setString(1, authMethod);
            st.setString(2, loginUuid.toString());
            try (var rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("original_profile_id");
                }
                return null;
            }
        }
    }

    @SneakyThrows
    @Override
    public void setLoginProfile(String authMethod, UUID loginUuid, int profileId) {
        setLoginProfileWithOriginalId(authMethod, loginUuid, profileId, profileId);
    }

    @SneakyThrows
    public void setLoginProfileWithOriginalId(String authMethod, UUID loginUuid, int profileId, int originalProfileId) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("""
                        INSERT INTO %s (auth_method, login_uuid, profile_id, original_profile_id)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE profile_id = ?, original_profile_id = original_profile_id
                        """.formatted(TABLE_LOGIN_PROFILE))
        ) {
            st.setString(1, authMethod);
            st.setString(2, loginUuid.toString());
            st.setInt(3, profileId);
            st.setInt(4, originalProfileId);
            st.setInt(5, profileId);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public void setProfileName(int id, String name) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("UPDATE %s SET name = ? WHERE id = ?".formatted(TABLE_PROFILES))
        ) {
            st.setString(1, name);
            st.setInt(2, id);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public void setPreferred(String username, String method) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("""
                        INSERT INTO %s (username, preferred) VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE preferred = ?
                        """.formatted(TABLE_AUTH_METHODS))
        ) {
            st.setString(1, username);
            st.setString(2, method);
            st.setString(3, method);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public String getPreferredMethod(String username) {
        try (
                Connection connection = getConnection();
                var st = connection.prepareStatement("SELECT preferred FROM %s WHERE username = ?".formatted(TABLE_AUTH_METHODS))
        ) {
            st.setString(1, username);
            try (var rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("preferred");
                } else {
                    return null;
                }
            }
        }
    }
}