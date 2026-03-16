package cn.jason31416.multiauth.api;

import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface IDatabaseHandler {
    Connection getConnection() throws SQLException;

    void setUUID(String username, UUID uuid);

    UUID getUUID(String username);

    void setPreferred(String username, String method);

    void addAuthMethod(String username, String method);

    List<String> getAuthMethods(String username);

    String getPreferredMethod(String username);
}
