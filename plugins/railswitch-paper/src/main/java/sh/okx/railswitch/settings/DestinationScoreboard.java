package sh.okx.railswitch.settings;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import vg.civcraft.mc.civmodcore.players.scoreboard.side.CivScoreBoard;
import vg.civcraft.mc.civmodcore.players.scoreboard.side.ScoreBoardAPI;

/**
 * Shows a player's rail destination on the civmodcore sidebar scoreboard.
 */
public final class DestinationScoreboard {

    private static final String BOARD_KEY = "railSwitchDest";

    // CivScoreBoard truncates the displayed line at 40 chars but caches the full string,
    // so an over-long line leaves a stale entry that never clears. The "Dest: " label plus
    // colour codes is 10 chars, leaving 30 for the value.
    private static final int MAX_DESTINATION_LENGTH = 30;

    private final CivScoreBoard board;

    public DestinationScoreboard() {
        this.board = ScoreBoardAPI.createBoard(BOARD_KEY);
    }

    /**
     * Shows the destination line for the player, or hides it when there is no destination.
     */
    public void update(Player player, String destination) {
        if (player == null) {
            return;
        }
        String line = render(destination);
        if (line == null) {
            board.hide(player);
        } else {
            board.set(player, line);
        }
    }

    public void delete() {
        ScoreBoardAPI.deleteBoard(board);
    }

    static String render(String destination) {
        if (destination == null || destination.isBlank()) {
            return null;
        }
        String value = destination.length() > MAX_DESTINATION_LENGTH
            ? destination.substring(0, MAX_DESTINATION_LENGTH)
            : destination;
        return ChatColor.GOLD + "Dest: " + ChatColor.LIGHT_PURPLE + value;
    }
}
