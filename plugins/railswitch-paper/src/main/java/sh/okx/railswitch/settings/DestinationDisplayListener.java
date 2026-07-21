package sh.okx.railswitch.settings;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Restores a player's destination sidebar line when they log in.
 */
public final class DestinationDisplayListener implements Listener {

    // NORMAL runs after civmodcore's ScoreBoardListener resets the scoreboard at LOWEST on join.
    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        SettingsManager.restoreDestinationDisplay(event.getPlayer());
    }
}
