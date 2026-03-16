package cn.jason31416.multiauth.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface IDatabaseHandler {
    Connection getConnection() throws SQLException;

    void setUUID(String username, UUID uuid);

    UUID getUUID(String username);

    UUID getUUIDByUsername(String username);

    String getUsernameByUUID(UUID uuid);

    /**
     * Resolves the effective username for the given Yggdrasil-authenticated player, persisting
     * the UUID→username mapping in the database.
     *
     * <ul>
     *   <li>If the UUID already has a stored username and the Yggdrasil name hasn't changed,
     *       the stored username is returned unchanged.</li>
     *   <li>If the Yggdrasil name changed (name-change on Mojang/skin-station side) and the
     *       new name is available, the stored mapping is updated to the new name.</li>
     *   <li>If the desired username is already occupied by a <em>different</em> UUID, the
     *       suffix {@code "1"} is appended to avoid a conflict.</li>
     * </ul>
     *
     * @param uuid           the UUID returned by the Yggdrasil authentication server
     * @param yggdrasilName  the player name returned by the Yggdrasil authentication server
     * @return the effective in-game username to use for this player
     */
    String resolveAndPersistUUID(UUID uuid, String yggdrasilName);

    void setPreferred(String username, String method);

    void addAuthMethod(String username, String method);

    List<String> getAuthMethods(String username);

    String getPreferredMethod(String username);
}
