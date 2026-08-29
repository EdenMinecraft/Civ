package world.edenmc.banstickvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * banstick-velocity is the single authoritative owner of direct-player ban state
 * (bs_player.bid / bs_ban / bs_ban_log in the shared `banstick` database). It gates
 * every login against that state fresh, and answers/enacts requests from
 * banstick-paper instances over Redis so neither Main nor PVP ever has to keep
 * their own copy that could go stale.
 */
@Plugin(id = "banstickvelocity", name = "BanStickVelocity", version = "1.0.0", authors = {"Aidan"})
public class BanStickVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;

    private CommentedConfigurationNode config;
    private DataSource dataSource;
    private JedisPool jedisPool;
    private BanStore banStore;
    private ExclusionStore exclusionStore;
    private RedisGateway redisGateway;
    private String banMessage;

    @Inject
    public BanStickVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        loadConfig(dataDirectory);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        connectDatabase();
        connectRedis();

        this.banStore = new BanStore(dataSource, logger);
        this.exclusionStore = new ExclusionStore(dataSource, logger);
        this.banMessage = config.node("banMessage").getString("You are banned.");

        this.redisGateway = new RedisGateway(server, logger, jedisPool, banStore, exclusionStore, banMessage);
        this.redisGateway.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (redisGateway != null) {
            redisGateway.stop();
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        banStore.getPlayerStatus(event.getPlayer().getUniqueId()).ban().ifPresent(status -> {
            String message = status.message() != null ? status.message() : banMessage;
            event.setResult(ResultedEvent.ComponentResult.denied(Component.text(message)));
        });
    }

    private void connectDatabase() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        CommentedConfigurationNode database = config.node("database");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mariadb://" + database.node("host").getString("localhost") + ":"
            + database.node("port").getInt(3306) + "/" + database.node("database").getString("banstick"));
        hikariConfig.setConnectionTimeout(database.node("connectionTimeout").getInt(10_000));
        hikariConfig.setIdleTimeout(database.node("idleTimeout").getInt(600_000));
        hikariConfig.setMaxLifetime(database.node("maxLifetime").getInt(7_200_000));
        hikariConfig.setMaximumPoolSize(database.node("poolsize").getInt(10));
        hikariConfig.setUsername(database.node("user").getString("root"));
        String password = database.node("password").getString();
        if (password != null && !password.isBlank()) {
            hikariConfig.setPassword(password);
        }
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    private void connectRedis() {
        CommentedConfigurationNode redis = config.node("redis");
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        String password = redis.node("password").getString();
        if (password != null && !password.isBlank()) {
            this.jedisPool = new JedisPool(poolConfig, redis.node("host").getString("localhost"),
                redis.node("port").getInt(6379), 2000, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, redis.node("host").getString("localhost"),
                redis.node("port").getInt(6379));
        }
    }

    private void loadConfig(Path dataDirectory) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Could not create data directory: {}", dataDirectory, e);
            return;
        }

        Path configFile = dataDirectory.resolve("config.yml");
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                    logger.info("Default configuration file created.");
                } else {
                    logger.error("Default configuration file is missing in resources!");
                    return;
                }
            } catch (IOException e) {
                logger.error("Could not create default configuration file: {}", configFile, e);
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(configFile).build();
        try {
            config = loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Could not load configuration file: " + configFile, e);
        }
    }
}
