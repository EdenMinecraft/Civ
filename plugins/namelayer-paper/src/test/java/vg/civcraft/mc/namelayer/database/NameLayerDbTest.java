package vg.civcraft.mc.namelayer.database;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.slf4j.helpers.NOPLogger;
import org.testcontainers.containers.MariaDBContainer;
import vg.civcraft.mc.civmodcore.dao.DatabaseCredentials;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;
import vg.civcraft.mc.namelayer.NameLayerPlugin;

/**
 * Base class for tests that exercise {@link GroupManagerDao} against a real MariaDB. A single
 * container is started for the whole test run (the JVM tears it down) and reused by every subclass,
 * so migrations only apply once per fresh schema. Data is wiped between tests via TRUNCATE rather
 * than re-migrating.
 *
 * <p>MockBukkit is mocked for the lifetime of each test class because civmodcore's ConnectionPool
 * and CivLogger reach for {@code Bukkit.getLogger()} during datasource construction.
 */
public abstract class NameLayerDbTest {

    private static final MariaDBContainer<?> MARIADB = startSharedContainer();

    // Per-test wipe targets. Deliberately excludes permissionIdMapping (seeded by runtime permission
    // registration) and the managed_plugin_* tables (track applied migrations) so the schema survives.
    private static final List<String> DATA_TABLES = List.of(
        "faction_member",
        "subgroup",
        "permissions",
        "permissionByGroup",
        "blacklist",
        "default_group",
        "group_invitation",
        "toggleAutoAccept",
        "nameLayerNameChanges",
        "faction",
        "faction_id");

    protected static GroupManagerDao dao;
    protected static ManagedDatasource datasource;

    private static ServerMock server;

    private static MariaDBContainer<?> startSharedContainer() {
        MariaDBContainer<?> container = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("namelayer");
        container.start();
        return container;
    }

    @BeforeAll
    static void setUpSchema() throws Exception {
        server = MockBukkit.mock();
        PluginMock realPlugin = MockBukkit.createMockPlugin();

        NameLayerPlugin plugin = mock(NameLayerPlugin.class);
        // JavaPlugin.getName() is final and reads pluginMeta; the subclass mock maker can't stub it,
        // so borrow a real PluginMock's meta to keep getName()/equals() from NPEing.
        copyPluginMeta(realPlugin, plugin);
        lenient().when(plugin.getSLF4JLogger()).thenReturn(NOPLogger.NOP_LOGGER);
        lenient().when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("namelayer-db-test"));
        setStaticPluginInstance(plugin);

        datasource = constructDatasource(MARIADB.getDatabaseName());
        if (datasource == null) {
            throw new IllegalStateException("Could not construct ManagedDatasource against the test container");
        }

        dao = new GroupManagerDao(java.util.logging.Logger.getLogger("namelayer-dao-test"), datasource);
        dao.registerMigrations();
        if (!datasource.updateDatabase()) {
            throw new IllegalStateException("namelayer migrations failed to apply to the test database");
        }
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void resetData() throws SQLException {
        try (Connection connection = datasource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : DATA_TABLES) {
                statement.execute("TRUNCATE TABLE " + table);
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /**
     * Builds a {@link ManagedDatasource} pointed at {@code databaseName} inside the shared container,
     * creating that database first if needed. Lets a subclass spin up a second, independently-migrated
     * schema (e.g. one frozen at a pre-collapse migration level) alongside the default one.
     */
    protected static ManagedDatasource constructDatasource(String databaseName) throws SQLException {
        // The container's `test` user only has rights on the initial database, so creating a second
        // schema (and granting `test` access to it) must run as root. Root shares the test password.
        String rootUrl = "jdbc:mariadb://" + MARIADB.getHost() + ":" + MARIADB.getFirstMappedPort() + "/";
        try (Connection connection = java.sql.DriverManager.getConnection(
                 rootUrl, "root", MARIADB.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + databaseName);
            statement.execute(
                "GRANT ALL PRIVILEGES ON " + databaseName + ".* TO '" + MARIADB.getUsername() + "'@'%'");
            statement.execute("FLUSH PRIVILEGES");
        }
        DatabaseCredentials credentials = new DatabaseCredentials(
            MARIADB.getUsername(),
            MARIADB.getPassword(),
            MARIADB.getHost(),
            MARIADB.getFirstMappedPort(),
            "mariadb",
            databaseName,
            5,
            10_000L,
            600_000L,
            7_200_000L);
        return ManagedDatasource.construct(NameLayerPlugin.getInstance(), credentials);
    }

    private static void setStaticPluginInstance(NameLayerPlugin plugin) throws Exception {
        Field instance = NameLayerPlugin.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, plugin);
    }

    private static void copyPluginMeta(Object from, Object to) throws Exception {
        Field meta = JavaPlugin.class.getDeclaredField("pluginMeta");
        meta.setAccessible(true);
        meta.set(to, meta.get(from));
    }
}
