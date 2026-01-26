package cn.jason31416.authx.api;

import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface IDatabaseHandler {
    Connection getConnection() throws SQLException;

    @SneakyThrows
    void setUUID(String username, UUID uuid);

    @SneakyThrows
    UUID getUUID(String username);

    @SneakyThrows
    void setPreferred(String username, String method);

    @SneakyThrows
    void addAuthMethod(String username, String method);

    @SneakyThrows
    List<String> getAuthMethods(String username);

    @SneakyThrows
    String getPreferredMethod(String username);
}
