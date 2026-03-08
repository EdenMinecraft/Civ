package com.programmerdan.minecraft.simpleadminhacks.configs;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHackConfig;
import org.bukkit.configuration.ConfigurationSection;

public class WikiHackConfig extends SimpleHackConfig {

    private String wikiUrl;
    private String linkText;

    public WikiHackConfig(SimpleAdminHacks plugin, ConfigurationSection base) {
        super(plugin, base);
    }

    @Override
    protected void wireup(ConfigurationSection config) {
        this.wikiUrl = config.getString("url", "https://edenmc.miraheze.org/wiki/Main_Page");
        this.linkText = config.getString("linkText", "[EdenMC Wiki]");
    }

    public String getWikiUrl() {
        return wikiUrl;
    }

    public String getLinkText() {
        return linkText;
    }
}
