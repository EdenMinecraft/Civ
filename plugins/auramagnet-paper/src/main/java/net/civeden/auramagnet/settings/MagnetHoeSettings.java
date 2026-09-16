package net.civeden.auramagnet.settings;

import java.util.EnumMap;
import java.util.Map;
import net.civeden.auramagnet.AuraMagnet;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vg.civcraft.mc.civmodcore.players.settings.PlayerSettingAPI;
import vg.civcraft.mc.civmodcore.players.settings.gui.MenuSection;
import vg.civcraft.mc.civmodcore.players.settings.impl.BooleanSetting;

/**
 * Registers the "MagnetHoe" /config section, with one toggle per magnetizable
 * material so players can pick exactly what their hoe pulls in.
 */
public class MagnetHoeSettings {

    private final Map<Material, MagnetItemToggleSetting> settingsByMaterial = new EnumMap<>(Material.class);
    private final BooleanSetting mainHandActivationSetting;

    public MagnetHoeSettings(AuraMagnet plugin) {
        MenuSection menu = PlayerSettingAPI.getMainMenu().createMenuSection(
            "MagnetHoe",
            "Choose which items your Magnet Hoe pulls in",
            new ItemStack(Material.DIAMOND_HOE));

        for (MagnetItem item : MagnetItem.values()) {
            MagnetItemToggleSetting setting = new MagnetItemToggleSetting(
                plugin, item.getMaterial(), item.getNiceName(), "auramagnet_" + item.name().toLowerCase());
            PlayerSettingAPI.registerSetting(setting, menu);
            settingsByMaterial.put(item.getMaterial(), setting);
        }

        mainHandActivationSetting = new BooleanSetting(plugin, true, "Activate From Main Hand",
            "auramagnet_mainhand_activation",
            "Lets your Magnet Hoe work from your main hand as well as your offhand. "
                + "Useful for Bedrock/Geyser players, who don't have access to an offhand slot.");
        PlayerSettingAPI.registerSetting(mainHandActivationSetting, menu);
    }

    /**
     * @return True if the given player has magnetizing the given material enabled.
     * Materials that aren't magnetizable at all are always false.
     */
    public boolean isEnabled(Player player, Material material) {
        MagnetItemToggleSetting setting = settingsByMaterial.get(material);
        return setting != null && setting.getValue(player);
    }

    /**
     * @return True if the given player wants their Magnet Hoe to activate from their
     * main hand in addition to their offhand.
     */
    public boolean isMainHandActivationEnabled(Player player) {
        return mainHandActivationSetting.getValue(player);
    }
}
