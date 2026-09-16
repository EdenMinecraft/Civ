package net.civeden.auramagnet.settings;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vg.civcraft.mc.civmodcore.players.settings.PlayerSetting;
import vg.civcraft.mc.civmodcore.players.settings.gui.MenuSection;

/**
 * Per-player on/off toggle for a single magnetizable material, shown in /config
 * using the item's own icon rather than a generic indicator.
 */
public class MagnetItemToggleSetting extends PlayerSetting<Boolean> {

    public MagnetItemToggleSetting(JavaPlugin owningPlugin, Material material, String niceName, String identifier) {
        super(owningPlugin, true, niceName, identifier, new ItemStack(material),
            "Toggle whether your Magnet Hoe pulls in " + niceName.toLowerCase() + ".", true);
    }

    @Override
    public Boolean deserialize(String serial) {
        return Boolean.parseBoolean(serial);
    }

    @Override
    public boolean isValidValue(String input) {
        return "true".equalsIgnoreCase(input) || "false".equalsIgnoreCase(input);
    }

    @Override
    public String serialize(Boolean value) {
        return String.valueOf(value);
    }

    @Override
    public String toText(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Enabled" : "Disabled";
    }

    @Override
    public void handleMenuClick(Player player, MenuSection menu) {
        setValue(player.getUniqueId(), !getValue(player.getUniqueId()));
        menu.showScreen(player);
    }
}
