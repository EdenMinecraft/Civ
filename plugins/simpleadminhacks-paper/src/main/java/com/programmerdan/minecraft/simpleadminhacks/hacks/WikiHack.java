package com.programmerdan.minecraft.simpleadminhacks.hacks;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.configs.WikiHackConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public class WikiHack extends SimpleHack<WikiHackConfig> implements Listener, CommandExecutor {

    public WikiHack(SimpleAdminHacks plugin, WikiHackConfig config) {
        super(plugin, config);
    }

    public static WikiHackConfig generate(SimpleAdminHacks plugin, ConfigurationSection config) {
        return new WikiHackConfig(plugin, config);
    }

    @Override
    public void onEnable() {
        plugin.registerListener(this);
        plugin.registerCommand("wiki", this);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Component link = Component.text(config.getLinkText())
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.openUrl(config.getWikiUrl()))
            .hoverEvent(HoverEvent.showText(
                Component.text(config.getWikiUrl()).color(NamedTextColor.GRAY)
            ));

        sender.sendMessage(
            Component.text("Wiki: ").color(NamedTextColor.GOLD).append(link)
        );
        return true;
    }
}
