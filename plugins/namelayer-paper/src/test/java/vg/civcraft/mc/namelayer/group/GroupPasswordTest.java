package vg.civcraft.mc.namelayer.group;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.TestDaoInjector;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;

/**
 * Regression tests for Group.isPassword. A group with no password used to NPE because it called
 * equals on a null field; it must return false instead.
 */
public class GroupPasswordTest {

    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @BeforeEach
    public void setUp() {
        GroupManagerDao dao = mock(GroupManagerDao.class);
        when(dao.getAllMembers(anyString())).thenReturn(Collections.emptyMap());
        when(dao.getAllIDs(anyString())).thenReturn(List.of(1));
        when(dao.getSubGroups(anyString())).thenReturn(Collections.emptyList());
        TestDaoInjector.inject(dao);
    }

    private Group groupWithPassword(String password) {
        return new Group("pwgroup", owner, false, password, 1, 0L, "red");
    }

    @Test
    public void noPasswordAnyGuessIsFalse() {
        Group group = groupWithPassword(null);
        assertFalse(group.isPassword("anything"));
    }

    @Test
    public void noPasswordNullGuessIsTrue() {
        Group group = groupWithPassword(null);
        assertTrue(group.isPassword(null));
    }

    @Test
    public void matchingPasswordIsTrue() {
        Group group = groupWithPassword("hunter2");
        assertTrue(group.isPassword("hunter2"));
    }

    @Test
    public void wrongPasswordIsFalse() {
        Group group = groupWithPassword("hunter2");
        assertFalse(group.isPassword("nope"));
    }

    @Test
    public void passwordSetNullGuessIsFalse() {
        Group group = groupWithPassword("hunter2");
        assertFalse(group.isPassword(null));
    }
}
