package vg.civcraft.mc.namelayer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.slf4j.helpers.NOPLogger;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;
import vg.civcraft.mc.namelayer.group.BlackList;
import vg.civcraft.mc.namelayer.group.Group;

/**
 * Regression test for the cancelled GroupCreateEvent in GroupManager.doCreateGroupAsync. A
 * cancelled create must not insert into the DAO; previously the cancel branch fell through to the
 * async insert.
 */
public class GroupCreateCancelTest {

    private ServerMock server;
    private PluginMock plugin;
    private GroupManagerDao dao;
    private GroupManager groupManager;

    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000dd");

    @BeforeEach
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        dao = mock(GroupManagerDao.class);
        when(dao.getAllMembers(anyString())).thenReturn(Collections.emptyMap());
        when(dao.getAllIDs(anyString())).thenReturn(List.of(1));
        when(dao.getSubGroups(anyString())).thenReturn(Collections.emptyList());
        when(dao.getGroup(anyInt())).thenReturn(null);
        TestDaoInjector.inject(dao);

        NameLayerPlugin instance = mock(NameLayerPlugin.class);
        // JavaPlugin.getName()/equals() are final and read the private pluginMeta field. The subclass
        // mock leaves it null, so plugin equality NPEs at MockBukkit unmock time. Borrow the real
        // PluginMock's meta so identity works.
        copyPluginMeta(plugin, instance);
        when(instance.getSLF4JLogger()).thenReturn(NOPLogger.NOP_LOGGER);
        when(instance.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        setStatic("instance", instance);
        setStatic("blackList", new BlackList());

        groupManager = new GroupManager();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
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

    private Group placeholderGroup() {
        return new Group("cancelgroup", owner, false, null, -1, 0L, "red");
    }

    private RunnableOnGroup noopPostCreate() {
        return new RunnableOnGroup() {
            @Override
            public void run() {
            }
        };
    }

    @Test
    public void cancelledCreateDoesNotInsert() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onCreate(vg.civcraft.mc.namelayer.events.GroupCreateEvent event) {
                event.setCancelled(true);
            }
        }, plugin);

        groupManager.createGroupAsync(placeholderGroup(), noopPostCreate(), false);
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);

        verify(dao, never()).createGroup(anyString(), any(UUID.class), any());
    }

    @Test
    public void uncancelledCreateDoesInsert() {
        when(dao.createGroup(anyString(), any(UUID.class), any())).thenReturn(-1);

        groupManager.createGroupAsync(placeholderGroup(), noopPostCreate(), false);
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);

        verify(dao, times(1)).createGroup(anyString(), any(UUID.class), any());
    }
}
