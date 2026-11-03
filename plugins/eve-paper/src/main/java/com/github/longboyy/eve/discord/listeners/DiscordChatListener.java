package com.github.longboyy.eve.discord.listeners;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageSentEvent;

public class DiscordChatListener extends DiscordListener {

    @Subscribe
    public void onDiscordChat(DiscordGuildMessagePreProcessEvent event) {
        
    }

}
