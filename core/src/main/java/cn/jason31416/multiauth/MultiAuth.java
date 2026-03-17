package cn.jason31416.multiauth;

import cn.jason31416.multiauth.handler.LoginSession;
import cn.jason31416.multiauth.hook.TABHandler;
import cn.jason31416.multiauth.command.AdminCommandHandler;
import cn.jason31416.multiauth.handler.DatabaseHandler;
import cn.jason31416.multiauth.handler.EventListener;
import cn.jason31416.multiauth.injection.PacketInjector;
import cn.jason31416.multiauth.injection.XLoginSessionHandler;
import cn.jason31416.multiauth.message.MessageLoader;
import cn.jason31416.multiauth.util.Config;
import cn.jason31416.multiauth.api.*;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Path;

@Plugin(
        id = "multiauth",
        name = "MultiAuth",
        version = "2.2.2",
        authors = {"Jason31416", "oneLiLi"},
        dependencies = {
                @Dependency(id = "tab", optional = true)
        }
)
public class MultiAuth implements MultiAuthApi {
    @Getter
    public static MultiAuth instance;

    @Getter
    private final Logger logger;
    @Getter
    private final ProxyServer proxy;
    @Getter
    private final File dataDirectory;

    @SneakyThrows
    public void init() {
        if(!dataDirectory.exists()) dataDirectory.mkdirs();

        Config.init();
        MessageLoader.initialize();

        DatabaseHandler.getInstance().init();
    }

    @Inject
    public MultiAuth(@Nonnull ProxyServer proxy, @Nonnull Logger logger, @Nonnull @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory.toFile();

        instance = this;
    }

    @Subscribe
    @SneakyThrows
    public void onProxyInitialization(ProxyInitializeEvent event) {
        cn.jason31416.multiauth.api.MultiAuth.setApiInstance(this);
        init();

        proxy.getEventManager().register(instance, new EventListener());

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("multiauth").plugin(this).build(),
                AdminCommandHandler.INSTANCE
        );

        try {
            XLoginSessionHandler.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize login session handler", e);
        }

        PacketInjector.inject();

        if(getProxy().getPluginManager().isLoaded("tab")){
            TABHandler.registerPlaceholder();
        }

        logger.info("MultiAuth has been enabled!");
    }

    public void info(String message) {
        logger.info(message);
    }

    public void error(String message) {
        logger.error(message);
    }

    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public IDatabaseHandler getDatabaseHandler() {
        return DatabaseHandler.getInstance();
    }

    @Override
    public String getVersion() {
        return "Beta 2.2";
    }

    @Override
    public ILoginSession getPlayerData(String username) {
        return LoginSession.getSession(username);
    }
}
