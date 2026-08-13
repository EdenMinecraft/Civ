package net.civeden.auramagnet;

import net.civeden.auramagnet.commands.AuraMagnetCommand;
import net.civeden.auramagnet.listeners.HoeTagListener;
import net.civeden.auramagnet.settings.MagnetHoeSettings;
import net.civeden.auramagnet.tasks.MagnetScanTask;
import org.bukkit.NamespacedKey;
import vg.civcraft.mc.civmodcore.ACivMod;

public class AuraMagnet extends ACivMod {

    private static AuraMagnet instance;

    public static NamespacedKey MAGNET_HOE_KEY;

    private MagnetHoeSettings magnetHoeSettings;
    private double magnetRadius;
    private int durabilityCostPerItem;
    private double durabilityChancePerItem;
    private String magnetLoreTrigger;

    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;
        saveDefaultConfig();

        MAGNET_HOE_KEY = new NamespacedKey(this, "aura_magnet_hoe");

        loadConfigValues();
        magnetHoeSettings = new MagnetHoeSettings(this);

        registerListener(new HoeTagListener());

        AuraMagnetCommand command = new AuraMagnetCommand();
        getCommand("auramagnet").setExecutor(command);
        getCommand("auramagnet").setTabCompleter(command);

        new MagnetScanTask().runTaskTimer(this, 10L, 5L);
    }

    private void loadConfigValues() {
        reloadConfig();
        magnetRadius = getConfig().getDouble("magnet-radius", 8.0);
        durabilityCostPerItem = getConfig().getInt("durability-cost-per-item", 1);
        durabilityChancePerItem = getConfig().getDouble("durability-chance-per-item", 1.0);
        magnetLoreTrigger = getConfig().getString("magnet-lore-trigger", "&6Aura Magnet Hoe");
    }

    public static AuraMagnet getInstance() {
        return instance;
    }

    public MagnetHoeSettings getMagnetHoeSettings() {
        return magnetHoeSettings;
    }

    public double getMagnetRadius() {
        return magnetRadius;
    }

    public int getDurabilityCostPerItem() {
        return durabilityCostPerItem;
    }

    public double getDurabilityChancePerItem() {
        return durabilityChancePerItem;
    }

    public String getMagnetLoreTrigger() {
        return magnetLoreTrigger;
    }
}
