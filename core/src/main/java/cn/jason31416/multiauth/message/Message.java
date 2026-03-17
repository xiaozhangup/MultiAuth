package cn.jason31416.multiauth.message;

import cn.jason31416.multiauth.util.ColorTransform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Message {

    private static final String PREFIX = "MultiAuth";
    private static final String BASE_COLOR = "#eaddc6";

    private static final String HEADER = "<dark_gray>[<color:" + BASE_COLOR + ">" + PREFIX + "</color>]</dark_gray> ";
    private static final String COLOR_MESSAGE = ColorTransform.white(BASE_COLOR, 10, 8);
    private static final String COLOR_ARGS = ColorTransform.white(BASE_COLOR, 10, 6);

    private String content;

    public Message(String content) {
        this.content = content;
    }

    public Message add(String placeholder, Object value) {
        String valStr = (value instanceof String) ? (String) value : value.toString();
        String replacement = "<color:" + COLOR_ARGS + ">" + valStr + "</color>";
        content = content.replace("%" + placeholder + "%", replacement);
        return this;
    }

    public String toString() {
        return content;
    }

    public Component toComponent() {
        String finalMessage = HEADER + "<color:" + COLOR_MESSAGE + ">" + content + "</color>";
        return MiniMessage.miniMessage().deserialize(finalMessage);
    }

    public Component toRawComponent() {
        return MiniMessage.miniMessage().deserialize(content);
    }

    public String toFormatted() {
        return MiniMessage.miniMessage().serialize(toComponent());
    }

    public boolean equals(Object obj) {
        if (obj instanceof Message) {
            return ((Message) obj).content.equals(content);
        }
        return false;
    }

    public static Message getMessage(String path) {
        return MessageLoader.getMessage(path);
    }
}