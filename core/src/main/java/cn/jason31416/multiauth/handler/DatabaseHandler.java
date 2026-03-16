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
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS authmethods (username VARCHAR(255) PRIMARY KEY, verified VARCHAR(255), preferred VARCHAR(255), modkey VARCHAR(255) default NULL)").execute();
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS uuiddata (username VARCHAR(255) PRIMARY KEY, uuid VARCHAR(255))").execute();
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS passwordbackup (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255), pubkeyhash VARCHAR(10))").execute();
        }
    }

    private HikariConfig buildDataSourceConfig() {
        HikariConfig config = new HikariConfig();
        String host = Config.getString("authentication.password.mysql.host");
        int port = Config.getInt("authentication.password.mysql.port");
        String database = Config.getString("authentication.password.mysql.database");
        String username = Config.getString("authentication.password.mysql.username");
        String password = Config.getString("authentication.password.mysql.password");
        String parameters = Config.getString("authentication.password.mysql.parameters");

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
            var st = connection.prepareStatement("INSERT INTO uuiddata (username, uuid) VALUES (?,?) AS new ON DUPLICATE KEY UPDATE uuid = new.uuid");
            st.setString(1, username);
            st.setString(2, uuid.toString());
            st.execute();
        }
    }
    @SneakyThrows
    @Override
    public UUID getUUID(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("SELECT uuid FROM uuiddata WHERE username =?");
            st.setString(1, username);
            var rs = st.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            } else {
                return UuidUtils.generateOfflinePlayerUuid(username);
            }
        }
    }

    @SneakyThrows
    public void setModKey(String username, String modkey){
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("UPDATE authmethods SET modkey =? WHERE username =?");
            st.setString(1, modkey);
            st.setString(2, username);
            st.execute();
        }
    }
    @SneakyThrows @Nullable
    public String getModKey(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("SELECT modkey FROM authmethods WHERE username =?");
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
            var st = connection.prepareStatement("INSERT INTO authmethods (username, verified, preferred) VALUES (?,?,?) AS new ON DUPLICATE KEY UPDATE preferred = new.preferred");
            st.setString(1, username);
            st.setString(2, "");
            st.setString(3, method);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public void addAuthMethod(String username, String method) { // it is assumed that user is already created
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("UPDATE authmethods SET verified = CONCAT(COALESCE(verified, ''), ?) WHERE username =?");
            st.setString(1, "," + method);
            st.setString(2, username);
            st.execute();
        }
    }

    @SneakyThrows
    @Override
    public List<String> getAuthMethods(String username) {
        try (Connection connection = getConnection()) {
            var st = connection.prepareStatement("SELECT verified FROM authmethods WHERE username =?");
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
            var st = connection.prepareStatement("SELECT preferred FROM authmethods WHERE username =?");
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
