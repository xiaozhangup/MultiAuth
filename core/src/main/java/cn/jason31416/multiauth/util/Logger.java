package cn.jason31416.multiauth.util;

import cn.jason31416.multiauth.MultiAuth;

public class Logger {
    public static void info(String message){
        MultiAuth.getInstance().info(message);
    }
    public static void warn(String message){
        MultiAuth.getInstance().warning(message);
    }
    public static void error(String message) {
        MultiAuth.getInstance().error(message);
    }
}
