package com.github.longboyy.eve;

import com.github.longboyy.eve.command.ForceGuildUpdateCommand;
import com.github.longboyy.eve.database.EveDAO;
import com.github.longboyy.eve.discord.DiscordCommandManager;
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

    private CommandManager commandManager;
    private EveDAO dao;
    private SnitchListener snitchListener;

    public EvePlugin() {
        this.config = new EvePluginConfig(this);
        this.discordCommandManager = new DiscordCommandManager(this);
        this.relayManager = new RelayManager(this);
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

        // Register the provider up front so DiscordSRV's own ready-time sync includes our commands,
        // and keep the ready listener subscribed so DiscordSRV reconnects re-sync them too.
        this.discordCommandManager.registerProvider();
        this.discordReadyListener.register();
        JDA jda = DiscordSRV.getPlugin().getJda();
        if(jda != null && jda.getStatus() == JDA.Status.CONNECTED){
            this.setupDiscord();
        }
        this.registerListener(this.snitchListener);

        this.commandManager.registerCommand(new ForceGuildUpdateCommand(this));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.discordReadyListener.unregister();
        this.discordCommandManager.unregisterProvider();
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
