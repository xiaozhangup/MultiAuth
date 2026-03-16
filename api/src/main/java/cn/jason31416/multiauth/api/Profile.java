package cn.jason31416.multiauth.api;

import java.util.UUID;

public class Profile {
    public final int id;
    public final UUID uuid;
    public final String name;

    public Profile(int id, UUID uuid, String name) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
    }
}
