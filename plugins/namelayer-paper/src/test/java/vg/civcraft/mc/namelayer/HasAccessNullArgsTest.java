package vg.civcraft.mc.namelayer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.slf4j.helpers.NOPLogger;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;

/**
 * Regression test for hasAccess null-argument handling. A null group is a normal "deny" (an
 * unloaded or deleted group, routinely produced by movement handlers such as Bastion's overlay)
 * and must resolve quietly; previously it logged a stack trace every tick. A null perm is a real
 * caller bug and is still logged.
 */
public class HasAccessNullArgsTest {

    private ServerMock server;
    private PluginMock plugin;
    private GroupManagerDao dao;
    private GroupManager groupManager;
    private final List<LogRecord> records = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        dao = mock(GroupManagerDao.class);
        when(dao.getPermissionMapping()).thenReturn(Collections.emptyMap());
        TestDaoInjector.inject(dao);

        Logger logger = Logger.getLogger("hasaccess-null-test");
        logger.setUseParentHandlers(false);
        for (Handler old : logger.getHandlers()) {
            logger.removeHandler(old);
        }
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        NameLayerPlugin instance = mock(NameLayerPlugin.class);
        copyPluginMeta(plugin, instance);
        when(instance.getSLF4JLogger()).thenReturn(NOPLogger.NOP_LOGGER);
        when(instance.getLogger()).thenReturn(logger);
        setStatic("instance", instance);

        PermissionType.initialize();

        groupManager = new GroupManager();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void nullGroupDeniesWithoutLogging() {
        PermissionType perm = PermissionType.getPermission("MEMBERS");

        assertFalse(groupManager.hasAccess((Group) null, UUID.randomUUID(), perm));
        assertTrue(records.isEmpty(), "a null group is a normal deny and must not log");
    }

    @Test
    public void nullPermStillLogs() {
        Group group = mock(Group.class);

        assertFalse(groupManager.hasAccess(group, UUID.randomUUID(), null));
        assertFalse(records.isEmpty(), "a null perm is a caller bug and must still be logged");
    }

    private static void setStatic(String field, Object value) throws Exception {
        Field f = NameLayerPlugin.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void copyPluginMeta(Object from, Object to) throws Exception {
        Field meta = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("pluginMeta");
        meta.setAccessible(true);
        meta.set(to, meta.get(from));
    }
}
