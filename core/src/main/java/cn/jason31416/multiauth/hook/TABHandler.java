package cn.jason31416.multiauth.hook;

import cn.jason31416.multiauth.handler.PlaceholderHandler;
import cn.jason31416.multiauth.util.Logger;
import me.neznamy.tab.api.TabAPI;

public class TABHandler {
    public static void registerPlaceholder(){
        TabAPI.getInstance().getPlaceholderManager()
                .registerPlayerPlaceholder("%authx-auth-tag%", 5000, player-> PlaceholderHandler.getPlayerOnlineText(player.getName()));
        Logger.info("Hooked into TAB!");
    }
}
