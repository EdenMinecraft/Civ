package com.github.longboyy.creativeworlds;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import vg.civcraft.mc.civmodcore.config.ConfigParser;

public class CreativeWorldsConfig extends ConfigParser {

    public CreativeWorldsConfig(Plugin plugin) {
        super(plugin);
    }


    @Override
    protected boolean parseInternal(ConfigurationSection config) {

        return true;
    }
}
