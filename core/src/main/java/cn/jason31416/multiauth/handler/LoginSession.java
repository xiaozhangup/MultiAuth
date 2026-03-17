package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.util.Logger;
import cn.jason31416.multiauth.api.ILoginSession;
import com.velocitypowered.api.util.UuidUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class LoginSession implements ILoginSession {
    @Getter
    private static Map<String, LoginSession> sessionMap = new ConcurrentHashMap<>();

    private String username;
    private UUID uuid;

    @Setter
    private String authMethod = null;

    public LoginSession(String username, UUID uuid) {
        this.username = username;
        this.uuid = uuid;
    }

    public static LoginSession getSession(String username) {
        if(!sessionMap.containsKey(username)){
            Logger.error("Unknown session found for username: "+username+"! Things might break.");
            return new LoginSession(username, UuidUtils.generateOfflinePlayerUuid(username));
        }
        return sessionMap.get(username);
    }
}
