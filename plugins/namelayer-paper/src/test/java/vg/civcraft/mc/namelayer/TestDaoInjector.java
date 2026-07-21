package vg.civcraft.mc.namelayer;

import java.lang.reflect.Field;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;
import vg.civcraft.mc.namelayer.group.Group;

/**
 * namelayer holds its DAO in private static fields populated from NameLayerPlugin during plugin
 * enable. Tests never run a real plugin, so we set those fields directly with a mock.
 */
public final class TestDaoInjector {

    private TestDaoInjector() {
    }

    public static void inject(GroupManagerDao dao) {
        setStatic(Group.class, "db", dao);
        setStatic(GroupManager.class, "groupManagerDao", dao);
        setStatic(NameLayerPlugin.class, "groupManagerDao", dao);
    }

    private static void setStatic(Class<?> owner, String field, Object value) {
        try {
            Field f = owner.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject test DAO into " + owner.getSimpleName() + "." + field, e);
        }
    }
}
