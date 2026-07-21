package vg.civcraft.mc.namelayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;

/** Characterization tests for the pure PlayerType id/name mappings. */
public class PlayerTypeTest {

    @Test
    public void idRoundTrips() {
        for (PlayerType type : PlayerType.values()) {
            assertEquals(type, PlayerType.getByID(PlayerType.getID(type)));
        }
    }

    @Test
    public void knownIdMapping() {
        assertEquals(0, PlayerType.getID(PlayerType.NOT_BLACKLISTED));
        assertEquals(1, PlayerType.getID(PlayerType.MEMBERS));
        assertEquals(2, PlayerType.getID(PlayerType.MODS));
        assertEquals(3, PlayerType.getID(PlayerType.ADMINS));
        assertEquals(4, PlayerType.getID(PlayerType.OWNER));
        assertEquals(-1, PlayerType.getID(null));
    }

    @Test
    public void unknownIdIsNull() {
        assertNull(PlayerType.getByID(99));
        assertNull(PlayerType.getByID(-1));
    }

    @Test
    public void byNameIsCaseInsensitive() {
        assertEquals(PlayerType.OWNER, PlayerType.getPlayerType("owner"));
        assertEquals(PlayerType.OWNER, PlayerType.getPlayerType("OWNER"));
        assertNull(PlayerType.getPlayerType("nope"));
    }

    @Test
    public void niceRankNames() {
        assertEquals("Member", PlayerType.getNiceRankName(PlayerType.MEMBERS));
        assertEquals("Mod", PlayerType.getNiceRankName(PlayerType.MODS));
        assertEquals("Admin", PlayerType.getNiceRankName(PlayerType.ADMINS));
        assertEquals("Owner", PlayerType.getNiceRankName(PlayerType.OWNER));
        assertEquals("RANK_ERROR", PlayerType.getNiceRankName(null));
    }
}
