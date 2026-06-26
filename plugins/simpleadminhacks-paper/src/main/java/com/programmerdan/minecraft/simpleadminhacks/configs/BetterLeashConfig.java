package com.programmerdan.minecraft.simpleadminhacks.configs;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHackConfig;
import org.bukkit.configuration.ConfigurationSection;

public class BetterLeashConfig extends SimpleHackConfig {

    private double leashDistance;

    public BetterLeashConfig(SimpleAdminHacks plugin, ConfigurationSection base) {
        super(plugin, base);
    }

    @Override
    protected void wireup(ConfigurationSection config) {
        this.leashDistance = config.getDouble("leashDistance", 20D);
    }

    public double getLeashDistance(){
        return this.leashDistance;
    }
}
