package cn.jason31416.multiauth.command;

import cn.jason31416.multiauth.MultiAuth;
import cn.jason31416.multiauth.api.Profile;
import cn.jason31416.multiauth.handler.DatabaseHandler;
import cn.jason31416.multiauth.handler.LegacyDataMigrator;
import cn.jason31416.multiauth.handler.LoginSession;
import cn.jason31416.multiauth.handler.Whitelist;
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
            case "whitelist" -> {
                handleWhitelistCommand(invocation);
            }
            case "migrate" -> {
                handleMigrateCommand(invocation);
            }
            case "query" -> {
                handleQueryCommand(invocation);
            }
            case "list" -> {
                handleListCommand(invocation);
            }
            case "" -> {
                invocation.source().sendMessage(new Message("<green>Running <aqua><bold>MultiAuth v2</bold></aqua> by Jason31416!").toComponent());
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

    private void handleWhitelistCommand(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Message.getMessage("command.whitelist.invalid-format").toComponent());
            return;
        }
        String sub = invocation.arguments()[1];
        switch (sub) {
            case "add" -> {
                // /multiauth whitelist add <auth_method> <player>
                if (invocation.arguments().length < 4) {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.add.invalid-format").toComponent());
                    return;
                }
                String authMethod = invocation.arguments()[2];
                String playerName = invocation.arguments()[3];
                Whitelist.getInstance().addPlayer(authMethod, playerName);
                invocation.source().sendMessage(Message.getMessage("command.whitelist.add.success")
                        .add("player", playerName)
                        .add("auth_method", authMethod)
                        .toComponent());
            }
            case "remove" -> {
                // /multiauth whitelist remove <auth_method> <player>
                if (invocation.arguments().length < 4) {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.remove.invalid-format").toComponent());
                    return;
                }
                String authMethod = invocation.arguments()[2];
                String playerName = invocation.arguments()[3];
                boolean removed = Whitelist.getInstance().removePlayer(authMethod, playerName);
                if (removed) {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.remove.success")
                            .add("player", playerName)
                            .add("auth_method", authMethod)
                            .toComponent());
                } else {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.remove.not-found")
                            .add("player", playerName)
                            .add("auth_method", authMethod)
                            .toComponent());
                }
            }
            case "list" -> {
                // /multiauth whitelist list <auth_method>
                if (invocation.arguments().length < 3) {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.list.invalid-format").toComponent());
                    return;
                }
                String authMethod = invocation.arguments()[2];
                java.util.Set<String> players = Whitelist.getInstance().getPlayers(authMethod);
                if (players.isEmpty()) {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.list.empty")
                            .add("auth_method", authMethod)
                            .toComponent());
                } else {
                    invocation.source().sendMessage(Message.getMessage("command.whitelist.list.header")
                            .add("auth_method", authMethod)
                            .add("count", String.valueOf(players.size()))
                            .toComponent());
                    invocation.source().sendMessage(new Message(String.join(", ", players)).toComponent());
                }
            }
            default -> {
                invocation.source().sendMessage(Message.getMessage("command.whitelist.invalid-format").toComponent());
            }
        }
    }

    private void handleMigrateCommand(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length != 1) {
            invocation.source().sendMessage(Message.getMessage("command.migrate.invalid-format").toComponent());
            return;
        }

        invocation.source().sendMessage(Message.getMessage("command.migrate.started").toComponent());

        try {
            LegacyDataMigrator.MigrationResult result = LegacyDataMigrator.getInstance().migrate();
            invocation.source().sendMessage(Message.getMessage("command.migrate.finished")
                    .add("total", result.totalRows)
                    .add("migrated", result.migratedRows)
                    .add("skipped", result.skippedRows)
                    .add("failed", result.failedRows)
                    .toComponent());
        } catch (Exception e) {
            invocation.source().sendMessage(Message.getMessage("command.migrate.failed")
                    .add("error", e.getMessage())
                    .toComponent());
        }
    }

    private void handleQueryCommand(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Message.getMessage("command.query.invalid-format").toComponent());
            return;
        }
        String playerName = invocation.arguments()[1];
        // First try to get profile from the database by name
        Profile profile = DatabaseHandler.getInstance().getProfileByName(playerName);
        String authMethod = DatabaseHandler.getInstance().getPreferredMethod(playerName);
        // If the player is currently online, prefer the live session data for auth method
        LoginSession session = LoginSession.getSessionMap().get(playerName);
        if (session != null && session.getAuthMethod() != null) {
            authMethod = session.getAuthMethod();
        }
        if (profile != null) {
            invocation.source().sendMessage(Message.getMessage("command.query.result")
                    .add("player", playerName)
                    .add("id", String.valueOf(profile.id))
                    .add("uuid", profile.uuid.toString())
                    .add("auth_method", authMethod != null ? authMethod : "unknown")
                    .toComponent());
        } else {
            invocation.source().sendMessage(Message.getMessage("command.query.not-found")
                    .add("player", playerName)
                    .toComponent());
        }
    }

    private void handleListCommand(SimpleCommand.Invocation invocation) {
        var sessions = LoginSession.getSessionMap();
        if (sessions.isEmpty()) {
            invocation.source().sendMessage(Message.getMessage("command.list.empty").toComponent());
            return;
        }
        invocation.source().sendMessage(Message.getMessage("command.list.header")
                .add("count", String.valueOf(sessions.size()))
                .toComponent());
        for (LoginSession session : sessions.values()) {
            String authMethod = session.getAuthMethod() != null ? session.getAuthMethod() : "unknown";
            invocation.source().sendMessage(Message.getMessage("command.list.entry")
                    .add("player", session.getUsername())
                    .add("uuid", session.getUuid().toString())
                    .add("auth_method", authMethod)
                    .toComponent());
        }
    }

    @Override
    public List<String> suggest(final @Nonnull Invocation invocation) {
        if(invocation.arguments().length<=1)
            return List.of("reload", "profile", "whitelist", "query", "list");
        else if(invocation.arguments().length == 2){
            return switch (invocation.arguments()[0]){
                case "profile" -> List.of("create", "set", "rename", "info");
                case "whitelist" -> List.of("add", "remove", "list");
                case "query" -> new java.util.ArrayList<>(LoginSession.getSessionMap().keySet());
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
                case "whitelist" -> switch (invocation.arguments()[1]) {
                    case "add", "remove", "list" -> List.of(Message.getMessage("tab-complete.profile.auth-method").toString());
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
                case "whitelist" -> switch (invocation.arguments()[1]) {
                    case "add", "remove" -> List.of(Message.getMessage("tab-complete.whitelist.player").toString());
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