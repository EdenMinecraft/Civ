package sh.okx.railswitch.settings;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import sh.okx.railswitch.RailSwitchPlugin;

/**
 * Manages the initialisation and registration of menu settings.
 */
public final class SettingsManager {

    private static RailSwitchMenu menu;

    private static DestinationSetting destSetting;

    private static ResetSetting resetSetting;

    private static DestinationScoreboard scoreboard;

    /**
     * Initialise the settings manager. This should only be called within RailSwitch onEnable().
     *
     * @param plugin The enabled RailSwitch plugin instance.
     */
    public static void init(RailSwitchPlugin plugin) {
        // Create the menu elements
        menu = new RailSwitchMenu();
        destSetting = new DestinationSetting(plugin);
        resetSetting = new ResetSetting(plugin, destSetting);
        // Register those elements
        menu.registerToParentMenu();
        menu.registerSetting(destSetting);
        menu.registerSetting(resetSetting);

        // Mirror the destination onto the sidebar scoreboard whenever it changes.
        // setValue() fires listeners BEFORE storing the new value, so use newValue here, not getDestination().
        scoreboard = new DestinationScoreboard();
        destSetting.registerListener((uuid, setting, oldValue, newValue) -> {
            if (scoreboard != null) {
                scoreboard.update(Bukkit.getPlayer(uuid), newValue);
            }
        });
    }

    /**
     * Gracefully resets the settings manager. This should only be called within RailSwitch onDisable().
     */
    public static void reset() {
        // TODO: Deregister and unload all the menu elements once PlayerSettingAPI becomes reload safe
        if (scoreboard != null) {
            scoreboard.delete();
            scoreboard = null;
        }
        menu = null;
        destSetting = null;
        resetSetting = null;
    }

    /**
     * Sets a player's destination. This is for when the player settings don't themselves provide a way to do so.
     *
     * @param player      The player to set the destination to.
     * @param destination The destination to set.
     */
    public static void setDestination(Player player, String destination) {
        Preconditions.checkArgument(player != null);
        if (Strings.isNullOrEmpty(destination)) {
            if (resetSetting != null) {
                resetSetting.resetPlayerDestination(player);
                // Do not put a message here since the message is sent in the method above.
            } else {
                player.sendMessage(ChatColor.RED + "Could not reset your destination.");
            }
        } else {
            if (destSetting != null) {
                destSetting.setValue(player, destination);
                player.sendMessage(ChatColor.GREEN + "Destination set to: " + destination);
            } else {
                player.sendMessage(ChatColor.RED + "Could not set your destination.");
            }
        }
    }

    /**
     * Gets a player's destination.
     *
     * @param player The player to get the destination for.
     * @return Returns the player's destination, which will never be null.
     */
    public static String getDestination(Player player) {
        String value = destSetting.getValue(player);
        if (value == null) {
            return "";
        }
        return value;
    }

    /**
     * Restores the player's destination line on the sidebar, e.g. when they log in.
     * Reads the stored value directly because player settings are already loaded at join time.
     *
     * @param player The player whose destination line should be refreshed.
     */
    public static void restoreDestinationDisplay(Player player) {
        if (scoreboard != null) {
            scoreboard.update(player, getDestination(player));
        }
    }

}
