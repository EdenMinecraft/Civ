package sh.okx.railswitch.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class DestinationScoreboardTest {

    @Test
    void render_nullDestination_returnsNull() {
        assertNull(DestinationScoreboard.render(null));
    }

    @Test
    void render_emptyDestination_returnsNull() {
        assertNull(DestinationScoreboard.render(""));
    }

    @Test
    void render_blankDestination_returnsNull() {
        assertNull(DestinationScoreboard.render("   "));
    }

    @Test
    void render_value_returnsLabelledColouredLine() {
        String expected = ChatColor.GOLD + "Dest: " + ChatColor.LIGHT_PURPLE + "Spawn";
        assertEquals(expected, DestinationScoreboard.render("Spawn"));
    }

    @Test
    void render_longValue_isCappedToFitScoreboardLine() {
        String line = DestinationScoreboard.render("A".repeat(60));
        assertEquals(ChatColor.GOLD + "Dest: " + ChatColor.LIGHT_PURPLE + "A".repeat(30), line);
    }
}
