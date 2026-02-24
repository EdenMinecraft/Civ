package com.github.longboyy.eve.discord.listeners;

import com.github.longboyy.eve.discord.DiscordCommandManager;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.dependencies.jda.api.events.guild.GuildJoinEvent;

public class DiscordGuildJoinListener extends DiscordListener {

    private final DiscordCommandManager commandManager;

    public DiscordGuildJoinListener(DiscordCommandManager commandManager){
        this.commandManager = commandManager;
    }

    @Subscribe
    public void onDiscordGuildJoin(GuildJoinEvent event){
        this.commandManager.registerCommandsForGuild(event.getGuild());
    }

}
