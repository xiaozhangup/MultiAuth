package cn.jason31416.authX.handler;

import cn.jason31416.authX.message.Message;
import cn.jason31416.authX.util.Config;
import cn.jason31416.authX.util.Logger;
import com.velocitypowered.api.proxy.Player;

public class PlaceholderHandler {
    public static String getPlayerOnlineText(String player) {
//        Logger.info(player+" with auth method "+LoginSession.getSession(player).getAuthMethod()+" and "+Config.getString("prefix."+LoginSession.getSession(player).getAuthMethod()));
        if(LoginSession.getSession(player).getAuthMethod()==null) return "";
        return new Message(Config.getString("prefix."+LoginSession.getSession(player).getAuthMethod())).toString();
    }
}
