package vg.civcraft.mc.namelayer.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;
import vg.civcraft.mc.namelayer.TestDaoInjector;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;

/**
 * Characterization tests pinning how the Group constructor turns per-PlayerType member lists from
 * the DAO into the players map. This is the safety net for a later refactor of that loop.
 */
public class GroupMembersTest {

    private GroupManagerDao dao;

    private final UUID ownerA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID ownerB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID admin = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID mod = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final UUID member1 = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private final UUID member2 = UUID.fromString("00000000-0000-0000-0000-000000000006");

    @BeforeEach
    public void setUp() {
        dao = mock(GroupManagerDao.class);
        when(dao.getAllMembers(anyString())).thenReturn(Collections.emptyMap());
        when(dao.getAllIDs(anyString())).thenReturn(List.of(1));
        when(dao.getSubGroups(anyString())).thenReturn(Collections.emptyList());
        TestDaoInjector.inject(dao);
    }

    private Group newGroup(String color) {
        return new Group("testgroup", ownerA, false, null, 1, 0L, color);
    }

    @Test
    public void playersMapMatchesDaoMembersPerType() {
        Map<UUID, PlayerType> daoMembers = new HashMap<>();
        daoMembers.put(ownerA, PlayerType.OWNER);
        daoMembers.put(ownerB, PlayerType.OWNER);
        daoMembers.put(admin, PlayerType.ADMINS);
        daoMembers.put(mod, PlayerType.MODS);
        daoMembers.put(member1, PlayerType.MEMBERS);
        daoMembers.put(member2, PlayerType.MEMBERS);
        when(dao.getAllMembers("testgroup")).thenReturn(daoMembers);

        Group group = newGroup("RED");

        Map<UUID, PlayerType> expected = new HashMap<>();
        expected.put(ownerA, PlayerType.OWNER);
        expected.put(ownerB, PlayerType.OWNER);
        expected.put(admin, PlayerType.ADMINS);
        expected.put(mod, PlayerType.MODS);
        expected.put(member1, PlayerType.MEMBERS);
        expected.put(member2, PlayerType.MEMBERS);

        Map<UUID, PlayerType> actual = new HashMap<>();
        for (UUID uuid : group.getAllMembers()) {
            actual.put(uuid, group.getPlayerType(uuid));
        }
        assertEquals(expected, actual);
    }

    @Test
    public void getAllMembersByTypeReturnsOnlyThatType() {
        Map<UUID, PlayerType> daoMembers = new HashMap<>();
        daoMembers.put(member1, PlayerType.MEMBERS);
        daoMembers.put(member2, PlayerType.MEMBERS);
        daoMembers.put(mod, PlayerType.MODS);
        when(dao.getAllMembers("testgroup")).thenReturn(daoMembers);

        Group group = newGroup("BLUE");

        assertEquals(Set.of(member1, member2), new HashSet<>(group.getAllMembers(PlayerType.MEMBERS)));
        assertEquals(List.of(mod), group.getAllMembers(PlayerType.MODS));
        assertEquals(List.of(), group.getAllMembers(PlayerType.OWNER));
    }

    @Test
    public void laterTypeOverwritesEarlierTypeForSameUuid() {
        // A uuid listed under multiple roles resolves to the highest-precedence role (the same
        // winning role the old per-type ctor loop produced via last put in values() order). The DAO
        // collapses multi-role rows into one entry per uuid, so we pin the post-collapse result.
        Map<UUID, PlayerType> daoMembers = new HashMap<>();
        daoMembers.put(member1, PlayerType.OWNER);
        when(dao.getAllMembers("testgroup")).thenReturn(daoMembers);

        Group group = newGroup("GREEN");

        assertEquals(PlayerType.OWNER, group.getPlayerType(member1));
    }

    @Test
    public void emptyMembersGivesEmptyPlayersMap() {
        Group group = newGroup("WHITE");
        assertEquals(List.of(), group.getAllMembers());
    }
}
