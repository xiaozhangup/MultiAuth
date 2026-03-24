package cn.jason31416.multiauth.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public interface IDatabaseHandler {
    Connection getConnection() throws SQLException;

    /**
     * Creates a new profile with the given UUID and display name.
     * The UUID is immutable after creation.
     *
     * @param uuid the UUID for this profile (immutable)
     * @param name the display name
     * @return the auto-generated numeric profile ID
     */
    int createProfile(UUID uuid, String name) throws SQLException;

    /**
     * Returns the profile with the given numeric ID, or {@code null} if not found.
     */
    Profile getProfileById(int id);

    /**
     * Returns the profile associated with the given login (auth method + UUID),
     * or {@code null} if no mapping exists.
     */
    Profile getProfileByLogin(String authMethod, UUID loginUuid);

    /**
     * Returns the profile with the given display name, or {@code null} if not found.
     */
    Profile getProfileByName(String name);

    /**
     * Returns the profile for the given login, creating a new one if none exists.
     * When a profile is created automatically its UUID is set to {@code loginUuid}
     * and its name is set to {@code name}.
     */
    Profile getOrCreateProfileForLogin(String authMethod, UUID loginUuid, String name);

    /**
     * Associates the given login (auth method + UUID) with an existing profile.
     * Overwrites any previous mapping for that login.
     */
    void setLoginProfile(String authMethod, UUID loginUuid, int profileId);

    /**
     * Updates the display name of an existing profile.
     */
    void setProfileName(int id, String name);

    void setPreferred(String username, String method);

    String getPreferredMethod(String username);
}
