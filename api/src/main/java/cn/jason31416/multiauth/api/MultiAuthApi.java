package cn.jason31416.multiauth.api;

public interface MultiAuthApi {
    /**
     * Fetches an instance of the Database Handler that allows you to directly modify MultiAuth's database.
     * @return DatabaseHandler instance
     */
    IDatabaseHandler getDatabaseHandler();

    /**
     * Fetches the current version string. Example: 'Beta 2.2'
     * @return Version string
     */
    String getVersion();

    ILoginSession getPlayerData(String username);
}
