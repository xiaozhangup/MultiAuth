package cn.jason31416.multiauth.command;

import cn.jason31416.multiauth.MultiAuth;
import cn.jason31416.multiauth.api.Profile;
import cn.jason31416.multiauth.handler.DatabaseHandler;
import cn.jason31416.multiauth.message.Message;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
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
            case "reload" -> {
                MultiAuth.instance.init();
                invocation.source().sendMessage(Message.getMessage("command.reload.success").toComponent());
            }
            case "profile" -> {
                handleProfileCommand(invocation);
            }
            case "" -> {
                invocation.source().sendMessage(new Message("<green>Running <aqua><bold>AuthX v2</bold></aqua> by Jason31416!").toComponent());
            }
            default -> {
                invocation.source().sendMessage(Message.getMessage("command.default").toComponent());
            }
        }
    }

    private void handleProfileCommand(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Message.getMessage("command.profile.invalid-format").toComponent());
            return;
        }
        String sub = invocation.arguments()[1];
        switch (sub) {
            case "create" -> {
                // /multiauth profile create <name>
                if (invocation.arguments().length < 3) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.create.invalid-format").toComponent());
                    return;
                }
                String name = invocation.arguments()[2];
                UUID uuid = UUID.randomUUID();
                try {
                    int id = DatabaseHandler.getInstance().createProfile(uuid, name);
                    invocation.source().sendMessage(Message.getMessage("command.profile.create.success")
                            .add("id", String.valueOf(id))
                            .add("uuid", uuid.toString())
                            .add("name", name)
                            .toComponent());
                } catch (Exception e) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.create.failed")
                            .add("error", e.getMessage())
                            .toComponent());
                }
            }
            case "set" -> {
                // /multiauth profile set <auth_method> <login_uuid> <profile_id>
                if (invocation.arguments().length < 5) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.set.invalid-format").toComponent());
                    return;
                }
                String authMethod = invocation.arguments()[2];
                UUID loginUuid;
                try {
                    loginUuid = UUID.fromString(invocation.arguments()[3]);
                } catch (Exception e) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.set.invalid-uuid").toComponent());
                    return;
                }
                int profileId;
                try {
                    profileId = Integer.parseInt(invocation.arguments()[4]);
                } catch (NumberFormatException e) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.set.invalid-id").toComponent());
                    return;
                }
                Profile target = DatabaseHandler.getInstance().getProfileById(profileId);
                if (target == null) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.not-found")
                            .add("id", String.valueOf(profileId))
                            .toComponent());
                    return;
                }
                DatabaseHandler.getInstance().setLoginProfile(authMethod, loginUuid, profileId);
                invocation.source().sendMessage(Message.getMessage("command.profile.set.success")
                        .add("auth_method", authMethod)
                        .add("login_uuid", loginUuid.toString())
                        .add("id", String.valueOf(profileId))
                        .toComponent());
            }
            case "rename" -> {
                // /multiauth profile rename <profile_id> <new_name>
                if (invocation.arguments().length < 4) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.rename.invalid-format").toComponent());
                    return;
                }
                int profileId;
                try {
                    profileId = Integer.parseInt(invocation.arguments()[2]);
                } catch (NumberFormatException e) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.rename.invalid-id").toComponent());
                    return;
                }
                String newName = invocation.arguments()[3];
                Profile target = DatabaseHandler.getInstance().getProfileById(profileId);
                if (target == null) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.not-found")
                            .add("id", String.valueOf(profileId))
                            .toComponent());
                    return;
                }
                DatabaseHandler.getInstance().setProfileName(profileId, newName);
                invocation.source().sendMessage(Message.getMessage("command.profile.rename.success")
                        .add("id", String.valueOf(profileId))
                        .add("name", newName)
                        .toComponent());
            }
            case "info" -> {
                // /multiauth profile info <profile_id>
                if (invocation.arguments().length < 3) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.info.invalid-format").toComponent());
                    return;
                }
                int profileId;
                try {
                    profileId = Integer.parseInt(invocation.arguments()[2]);
                } catch (NumberFormatException e) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.info.invalid-id").toComponent());
                    return;
                }
                Profile target = DatabaseHandler.getInstance().getProfileById(profileId);
                if (target == null) {
                    invocation.source().sendMessage(Message.getMessage("command.profile.not-found")
                            .add("id", String.valueOf(profileId))
                            .toComponent());
                    return;
                }
                invocation.source().sendMessage(Message.getMessage("command.profile.info.result")
                        .add("id", String.valueOf(target.id))
                        .add("uuid", target.uuid.toString())
                        .add("name", target.name)
                        .toComponent());
            }
            default -> {
                invocation.source().sendMessage(Message.getMessage("command.profile.invalid-format").toComponent());
            }
        }
    }

    @Override
    public List<String> suggest(final @Nonnull Invocation invocation) {
        if(invocation.arguments().length<=1)
            return List.of("reload", "profile");
        else if(invocation.arguments().length == 2){
            return switch (invocation.arguments()[0]){
                case "profile" -> List.of("create", "set", "rename", "info");
                default -> List.of();
            };
        } else if (invocation.arguments().length == 3) {
            return switch (invocation.arguments()[0]) {
                case "profile" -> switch (invocation.arguments()[1]) {
                    case "create" -> List.of(Message.getMessage("tab-complete.profile.name").toString());
                    case "set" -> List.of(Message.getMessage("tab-complete.profile.auth-method").toString());
                    case "rename", "info" -> List.of(Message.getMessage("tab-complete.profile.id").toString());
                    default -> List.of();
                };
                default -> List.of();
            };
        } else if (invocation.arguments().length == 4) {
            return switch (invocation.arguments()[0]) {
                case "profile" -> switch (invocation.arguments()[1]) {
                    case "set" -> List.of(Message.getMessage("tab-complete.profile.login-uuid").toString());
                    case "rename" -> List.of(Message.getMessage("tab-complete.profile.name").toString());
                    default -> List.of();
                };
                default -> List.of();
            };
        } else if (invocation.arguments().length == 5) {
            return switch (invocation.arguments()[0]) {
                case "profile" -> switch (invocation.arguments()[1]) {
                    case "set" -> List.of(Message.getMessage("tab-complete.profile.id").toString());
                    default -> List.of();
                };
                default -> List.of();
            };
        }
        return List.of();
    }
}