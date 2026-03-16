package cn.jason31416.authX.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Message {
    public static MiniMessage miniMessage;
    String content;
    public Message(String content) {
        this.content = content;
    }
    public Message add(String placeholder, Object value){
        content = content.replace("%"+placeholder+"%", (value instanceof String)?(String)value:value.toString());
        return this;
    }
    public String toString(){
        return content;
    }
    public Component toComponent(){
        return MiniMessage.miniMessage().deserialize(content);
    }
    public String toFormatted(){
        return MiniMessage.miniMessage().serialize(MiniMessage.miniMessage().deserialize(content));
    }
    public boolean equals(Object obj){
        if(obj instanceof Message){
            return ((Message)obj).content.equals(content);
        }
        return false;
    }

    public static Message getMessage(String path){
        return MessageLoader.getMessage(path);
    }
}
