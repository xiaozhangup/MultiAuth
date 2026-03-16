package cn.jason31416.multiauth.handler;

import cn.jason31416.multiauth.message.Message;
import cn.jason31416.multiauth.util.Config;

public class PlaceholderHandler {
    public static String getPlayerOnlineText(String player) {
//        Logger.info(player+" with auth method "+LoginSession.getSession(player).getAuthMethod()+" and "+Config.getString("prefix."+LoginSession.getSession(player).getAuthMethod()));
        if(LoginSession.getSession(player).getAuthMethod()==null) return "";
        return new Message(Config.getString("prefix."+LoginSession.getSession(player).getAuthMethod())).toString();
    }
}
