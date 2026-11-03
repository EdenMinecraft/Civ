package com.github.longboyy.eve.discord.listeners;

import com.github.longboyy.eve.EvePlugin;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;

public class DiscordReadyListener extends DiscordListener {

    private static boolean _READY = false;

    public static boolean isReady(){
        return _READY;
    }

    private final EvePlugin plugin;

    public DiscordReadyListener(EvePlugin plugin){
        this.plugin = plugin;
    }

    @Subscribe
    public void onDiscordReady(DiscordReadyEvent event){
        _READY = true;
        this.plugin.setupDiscord();
    }

}
