package vg.civcraft.mc.namelayer.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.TestDaoInjector;
import vg.civcraft.mc.namelayer.database.GroupManagerDao;

/**
 * Characterization tests for the group color parsing in the Group constructor (Group.java ~82-86):
 * try the NamedTextColor registry first (lowercase names), then fall back to a hex string parse,
 * else null.
 */
public class GroupColorTest {

    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @BeforeEach
    public void setUp() {
        GroupManagerDao dao = mock(GroupManagerDao.class);
        when(dao.getAllMembers(anyString())).thenReturn(Collections.emptyMap());
        when(dao.getAllIDs(anyString())).thenReturn(List.of(1));
        when(dao.getSubGroups(anyString())).thenReturn(Collections.emptyList());
        TestDaoInjector.inject(dao);
    }

    private TextColor colorOf(String input) {
        return new Group("colorgroup", owner, false, null, 1, 0L, input).getGroupColor();
    }

    @Test
    public void namedColorLowercaseResolves() {
        assertEquals(NamedTextColor.RED, colorOf("red"));
        assertEquals(NamedTextColor.DARK_PURPLE, colorOf("dark_purple"));
    }

    @Test
    public void hexStringResolves() {
        assertEquals(TextColor.fromHexString("#ABCDEF"), colorOf("#ABCDEF"));
    }

    @Test
    public void uppercaseNamedColorIsNotFoundInRegistry() {
        // NamedTextColor.NAMES uses lowercase keys, so "RED" misses the registry and is not a hex
        // string either, yielding null. Pin this current (arguably surprising) behavior.
        assertNull(colorOf("RED"));
    }

    @Test
    public void unknownColorIsNull() {
        assertNull(colorOf("not_a_color"));
    }
}
