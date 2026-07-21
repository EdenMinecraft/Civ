package vg.civcraft.mc.namelayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.slf4j.helpers.NOPLogger;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;
import vg.civcraft.mc.namelayer.group.BlackList;
import vg.civcraft.mc.namelayer.group.Group;

/**
 * Regression test for the missing-group query storm: Citadel reinforcements and JukeAlert snitches
 * reference deleted group ids forever, and GroupManager.getGroup(int) queries on the calling (main)
 * thread. Without negative caching every lookup of a missing id was a blocking DB round-trip,
 * which stalled the main thread for seconds during fights.
 */
public class GroupIdNegativeCacheTest {

    private static final int MISSING_ID = 55;

    private PluginMock plugin;
    private GroupManagerDao dao;

    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000ee");

    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        dao = mock(GroupManagerDao.class);
        when(dao.getAllMembers(anyString())).thenReturn(Collections.emptyMap());
        when(dao.getAllIDs(anyString())).thenReturn(List.of(MISSING_ID));
        when(dao.getSubGroups(anyString())).thenReturn(Collections.emptyList());
        when(dao.getGroup(anyInt())).thenReturn(null);
        TestDaoInjector.inject(dao);

        NameLayerPlugin instance = mock(NameLayerPlugin.class);
        copyPluginMeta(plugin, instance);
        when(instance.getSLF4JLogger()).thenReturn(NOPLogger.NOP_LOGGER);
        when(instance.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        setStatic("instance", instance);
        setStatic("blackList", new BlackList());

        clearGroupManagerStatics();
    }

    @AfterEach
    public void tearDown() throws Exception {
        clearGroupManagerStatics();
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

    /** GroupManager's caches are static and leak across tests; reset them for isolation. */
    private static void clearGroupManagerStatics() throws Exception {
        for (String field : new String[] {"groupsByName", "groupsById", "missingGroupIds"}) {
            Field f = GroupManager.class.getDeclaredField(field);
            f.setAccessible(true);
            Object value = f.get(null);
            if (value instanceof Map<?, ?> map) {
                map.clear();
            } else if (value instanceof Collection<?> collection) {
                collection.clear();
            }
        }
    }

    @Test
    public void missingIdIsQueriedOnlyOnce() {
        assertNull(GroupManager.getGroup(MISSING_ID));
        assertNull(GroupManager.getGroup(MISSING_ID));
        assertNull(GroupManager.getGroup(MISSING_ID));

        verify(dao, times(1)).getGroup(MISSING_ID);
    }

    @Test
    public void invalidateCacheRetriesMissingId() {
        assertNull(GroupManager.getGroup(MISSING_ID));

        GroupManager.invalidateCache("somegroup");

        Group restored = new Group("restored", owner, false, null, MISSING_ID, 0L, "gray");
        when(dao.getGroup(MISSING_ID)).thenReturn(restored);

        assertEquals(restored, GroupManager.getGroup(MISSING_ID));
        verify(dao, times(2)).getGroup(MISSING_ID);
    }

    @Test
    public void foundGroupIsCachedPositively() {
        Group group = new Group("realgroup", owner, false, null, MISSING_ID, 0L, "gray");
        when(dao.getGroup(MISSING_ID)).thenReturn(group);

        assertEquals(group, GroupManager.getGroup(MISSING_ID));
        assertEquals(group, GroupManager.getGroup(MISSING_ID));

        verify(dao, times(1)).getGroup(MISSING_ID);
    }
}
