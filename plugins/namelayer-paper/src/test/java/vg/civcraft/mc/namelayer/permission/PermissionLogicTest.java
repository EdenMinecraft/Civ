package vg.civcraft.mc.namelayer.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;
import vg.civcraft.mc.namelayer.TestDaoInjector;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;
import vg.civcraft.mc.namelayer.group.Group;

/**
 * Characterization tests for the pure permission logic: the registered NameLayer PermissionTypes,
 * PermissionType.isOwnerPermission, and the GroupPermission add/remove/has/getFirst logic.
 * No real database; the DAO is mocked, which is enough for PermissionType.initialize and
 * GroupPermission to load.
 */
public class PermissionLogicTest {

    private static GroupManagerDao dao;

    @BeforeAll
    public static void initPermissions() {
        dao = mock(GroupManagerDao.class);
        when(dao.getPermissionMapping()).thenReturn(Collections.emptyMap());
        TestDaoInjector.inject(dao);
        PermissionType.initialize();
    }

    @BeforeEach
    public void reinject() {
        TestDaoInjector.inject(dao);
    }

    @Test
    public void nameLayerPermissionsAreRegistered() {
        assertEquals("MEMBERS", PermissionType.getPermission("MEMBERS").getName());
        assertEquals("OWNER", PermissionType.getPermission("OWNER").getName());
        assertEquals("DELETE", PermissionType.getPermission("DELETE").getName());
        assertNull(PermissionType.getPermission("NOT_A_REAL_PERM"));
    }

    @Test
    public void isOwnerPermissionMatchesDefaultLevels() {
        // These perms default to {OWNER} only, so isOwnerPermission is true.
        assertTrue(PermissionType.getPermission("OWNER").isOwnerPermission());
        assertTrue(PermissionType.getPermission("DELETE").isOwnerPermission());
        assertTrue(PermissionType.getPermission("PERMS").isOwnerPermission());
        assertTrue(PermissionType.getPermission("ADMINS").isOwnerPermission());

        // MEMBERS defaults to {MODS, ADMINS, OWNER}; OPEN_GUI defaults to all four types.
        assertFalse(PermissionType.getPermission("MEMBERS").isOwnerPermission());
        assertFalse(PermissionType.getPermission("OPEN_GUI").isOwnerPermission());
    }

    private GroupPermission groupPermissionWith(Map<PlayerType, List<PermissionType>> stored) {
        Group group = mock(Group.class);
        when(group.getName()).thenReturn("permgroup");
        when(dao.getPermissions("permgroup")).thenReturn(new HashMap<>(stored));
        return new GroupPermission(group);
    }

    @Test
    public void hasPermissionReflectsStoredPerms() {
        PermissionType delete = PermissionType.getPermission("DELETE");
        Map<PlayerType, List<PermissionType>> stored = new HashMap<>();
        stored.put(PlayerType.OWNER, List.of(delete));
        GroupPermission gp = groupPermissionWith(stored);

        assertTrue(gp.hasPermission(PlayerType.OWNER, delete));
        assertFalse(gp.hasPermission(PlayerType.MEMBERS, delete));
        assertFalse(gp.hasPermission(null, delete));
        assertFalse(gp.hasPermission(PlayerType.OWNER, null));
    }

    @Test
    public void addAndRemovePermissionInMemory() {
        PermissionType perms = PermissionType.getPermission("PERMS");
        GroupPermission gp = groupPermissionWith(new HashMap<>());

        assertFalse(gp.hasPermission(PlayerType.OWNER, perms));
        assertTrue(gp.addPermission(PlayerType.OWNER, perms, false));
        assertTrue(gp.hasPermission(PlayerType.OWNER, perms));
        // Adding again is a no-op returning false.
        assertFalse(gp.addPermission(PlayerType.OWNER, perms, false));

        assertTrue(gp.removePermission(PlayerType.OWNER, perms, false));
        assertFalse(gp.hasPermission(PlayerType.OWNER, perms));
        // Removing something not present returns false.
        assertFalse(gp.removePermission(PlayerType.OWNER, perms, false));
    }

    @Test
    public void getFirstWithPermFindsAnyPlayerTypeHolding() {
        PermissionType members = PermissionType.getPermission("MEMBERS");
        Map<PlayerType, List<PermissionType>> stored = new HashMap<>();
        stored.put(PlayerType.MODS, List.of(members));
        GroupPermission gp = groupPermissionWith(stored);

        assertEquals(PlayerType.MODS, gp.getFirstWithPerm(members));
        assertNull(gp.getFirstWithPerm(PermissionType.getPermission("DELETE")));
    }
}
