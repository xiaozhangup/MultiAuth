package cn.jason31416.multiauth.api;

import lombok.Setter;
import org.jetbrains.annotations.Nullable;

public class MultiAuth {
    @Setter
    private static MultiAuthApi apiInstance=null;

    @Nullable
    public static MultiAuthApi getInstance(){
        if(apiInstance==null){
            return null;
        }
        return apiInstance;
    }
}
