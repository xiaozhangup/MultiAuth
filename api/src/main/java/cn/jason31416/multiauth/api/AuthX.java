package cn.jason31416.multiauth.api;

import lombok.Setter;
import javax.annotation.Nullable;

public class AuthX {
    @Setter
    private static AuthXApi apiInstance=null;

    @Nullable
    public static AuthXApi getInstance(){
        if(apiInstance==null){
            return null;
        }
        return apiInstance;
    }
}
