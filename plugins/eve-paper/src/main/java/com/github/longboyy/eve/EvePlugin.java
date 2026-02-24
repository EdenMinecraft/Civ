package com.github.longboyy.eve;

import com.github.longboyy.eve.command.ForceGuildUpdateCommand;
import com.github.longboyy.eve.database.EveDAO;
import com.github.longboyy.eve.discord.DiscordCommandManager;
import com.github.longboyy.eve.discord.listeners.DiscordGuildJoinListener;
import com.github.longboyy.eve.discord.listeners.DiscordReadyListener;
import com.github.longboyy.eve.listeners.SnitchListener;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import org.bukkit.event.HandlerList;
import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.civmodcore.commands.CommandManager;

public class EvePlugin extends ACivMod {
    private final EvePluginConfig config;
    private final DiscordCommandManager discordCommandManager;
    private final RelayManager relayManager;
    private final DiscordReadyListener discordReadyListener;
    private final DiscordGuildJoinListener discordGuildJoinListener;

    private CommandManager commandManager;
    private EveDAO dao;
    private SnitchListener snitchListener;

    public EvePlugin() {
        this.config = new EvePluginConfig(this);
        this.discordCommandManager = new DiscordCommandManager(this);
        this.relayManager = new RelayManager(this);
        this.discordGuildJoinListener = new DiscordGuildJoinListener(this.discordCommandManager);
        this.discordReadyListener = new DiscordReadyListener(this);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if(!this.config.parse()){
            this.disable();
        }
        EvePermissionHandler.setup();
        this.commandManager = new CommandManager(this);
        this.dao = new EveDAO(this.config.getDatabase());
        this.dao.updateDatabase();
        this.snitchListener = new SnitchListener(this);
        this.relayManager.reloadRelays();

        if(DiscordSRV.getPlugin().getJda().getStatus() == JDA.Status.CONNECTED){
            this.setupDiscord();
        }else{
            this.discordReadyListener.register();
        }

        this.discordGuildJoinListener.register();
        this.registerListener(this.snitchListener);

        this.commandManager.registerCommand(new ForceGuildUpdateCommand(this));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.discordReadyListener.unregister();
        this.discordGuildJoinListener.unregister();
        HandlerList.unregisterAll(this);
    }

    public EvePluginConfig getConfigManager(){
        return this.config;
    }

    public EveDAO getDatabase(){
        return this.dao;
    }

    public RelayManager getRelayManager(){
        return this.relayManager;
    }

    public DiscordCommandManager getDiscordCommandManager(){
        return this.discordCommandManager;
    }

    public void setupDiscord(){
        this.discordCommandManager.registerCommands();
    }
}
