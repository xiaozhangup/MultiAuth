package cn.jason31416.multiauth.command;

import cn.jason31416.multiauth.MultiAuth;
import cn.jason31416.multiauth.handler.DatabaseHandler;
import cn.jason31416.multiauth.handler.EventListener;
import cn.jason31416.multiauth.message.Message;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.UuidUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class AdminCommandHandler implements SimpleCommand {
    public static final AdminCommandHandler INSTANCE = new AdminCommandHandler();

    @Override
    public void execute(final @NotNull SimpleCommand.Invocation invocation) {
        String subCommand;
        if(invocation.arguments().length>0){
            subCommand = invocation.arguments()[0];
        }else{
            subCommand = "";
        }
        if((invocation.source() instanceof Player)&&!invocation.source().hasPermission("multiauth.admin")){
            invocation.source().sendMessage(Message.getMessage("command.no-permission").toComponent());
            return;
        }
        switch (subCommand){
            case "unregister" -> {
                if(invocation.arguments().length<2){
                    invocation.source().sendMessage(Message.getMessage("command.force-unregister.invalid-format").toComponent());
                    return;
                }
                String username = invocation.arguments()[1];
                DatabaseHandler.getInstance().setUUID(username, null);
                invocation.source().sendMessage(Message.getMessage("command.force-unregister.success").add("player", username).toComponent());
            }
            case "reload" -> {
                MultiAuth.instance.init();
                invocation.source().sendMessage(Message.getMessage("command.reload.success").toComponent());
            }
            case "setuuid" -> {
                if(invocation.arguments().length<2){
                    invocation.source().sendMessage(Message.getMessage("command.set-uuid.invalid-format").toComponent());
                    return;
                }
                UUID uuid;
                String username = invocation.arguments()[1];
                if(invocation.arguments().length >= 3){
                    try {
                        uuid = UUID.fromString(invocation.arguments()[2]);
                    }catch (Exception e){
                        invocation.source().sendMessage(Message.getMessage("command.set-uuid.invalid-uuid").toComponent());
                        return;
                    }
                }else{
                    uuid = UuidUtils.generateOfflinePlayerUuid(username);
                }
                DatabaseHandler.getInstance().setUUID(username, uuid);
                invocation.source().sendMessage(Message.getMessage("command.set-uuid.success").add("player", username).add("uuid", uuid.toString()).toComponent());
            }
            case "" -> {
                invocation.source().sendMessage(new Message("<green>Running <aqua><bold>AuthX v2</bold></aqua> by Jason31416!").toComponent());
            }
            default -> {
                invocation.source().sendMessage(Message.getMessage("command.default").toComponent());
            }
        }
    }
    @Override
    public List<String> suggest(final @Nonnull Invocation invocation) {
        if(invocation.arguments().length<=1)
            return List.of("unregister", "reload", "setuuid");
        else if(invocation.arguments().length == 2){
            return switch (invocation.arguments()[0]){
                case "unregister" -> List.of(Message.getMessage("tab-complete.force-unregister.player").toString());
                case "setuuid" -> List.of(Message.getMessage("tab-complete.set-uuid.player").toString());
                default -> List.of();
            };
        }else if(invocation.arguments().length == 3){
            return switch (invocation.arguments()[0]){
                case "setuuid" -> List.of(Message.getMessage("tab-complete.set-uuid.uuid").toString());
                default -> List.of();
            };
        }
        return List.of();
    }
}