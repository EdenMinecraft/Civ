package com.github.longboyy.eve.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import com.github.longboyy.eve.EvePlugin;
import github.scarsz.discordsrv.DiscordSRV;
import org.bukkit.entity.Player;

public class ForceGuildUpdateCommand extends BaseCommand {

    private final EvePlugin plugin;

    public ForceGuildUpdateCommand(EvePlugin plugin){
        this.plugin = plugin;
    }

    @CommandAlias("forceguildupdate")
    @CommandPermission("eve.forceguildupdate")
    public void onForceGuildUpdateCommand(Player player){
        plugin.getDiscordCommandManager().registerCommands();
        player.sendMessage("Updated discord commands");
    }

}
