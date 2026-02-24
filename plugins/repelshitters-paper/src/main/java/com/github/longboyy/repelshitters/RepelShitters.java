package com.github.longboyy.repelshitters;

import com.github.longboyy.repelshitters.commands.ReloadCommand;
import com.github.longboyy.repelshitters.listeners.CitadelListener;
import com.github.longboyy.repelshitters.listeners.ExilePearlListener;
import com.github.longboyy.repelshitters.listeners.MobListener;
import com.github.longboyy.repelshitters.listeners.PlayerListener;
import com.github.longboyy.repelshitters.listeners.GhastListener;
import org.bukkit.event.HandlerList;
import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.civmodcore.commands.CommandManager;
import java.util.Random;

public class RepelShitters extends ACivMod{

    public static final Random RANDOM = new Random();
    private final RepelShittersConfig config;
    private final ActivityManager activityManager;
    private final AutoPotManager autoPotManager;
    private final HappyGhastManager happyGhastManager;
    private final PlayerListener playerListener;
    private final CitadelListener citadelListener;
    private final GhastListener ghastListener;
    private final MobListener mobListener;
    //private final ExilePearlListener exilePearlListener;
    private CommandManager commandManager;

    public RepelShitters() {
        this.config = new RepelShittersConfig(this);
        this.activityManager = new ActivityManager(this);
        this.autoPotManager = new AutoPotManager(this);
        this.happyGhastManager = new HappyGhastManager(this);
        this.playerListener = new PlayerListener(this);
        this.citadelListener = new CitadelListener(this);
        this.ghastListener = new GhastListener(this);
        this.mobListener = new MobListener();
        //this.exilePearlListener = new ExilePearlListener(this);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if(!this.config.parse()){
            this.disable();
        }
        this.autoPotManager.registerSettings();
        //this.happyGhastManager.startDamageTask();
        this.registerListener(this.playerListener);
        this.registerListener(this.citadelListener);
        this.registerListener(this.ghastListener);
        this.registerListener(this.mobListener);
        //this.registerListener(this.exilePearlListener);
        this.commandManager = new CommandManager(this);
        this.commandManager.init();
        this.commandManager.registerCommand(new ReloadCommand(this));
        //this.registerListener(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.commandManager.unregisterCommands();
        HandlerList.unregisterAll(this);
        //this.happyGhastManager.stopDamageTask();
    }

    public RepelShittersConfig getConfigManager(){
        return this.config;
    }

    public ActivityManager getActivityManager(){
        return this.activityManager;
    }

    public AutoPotManager getAutoPotManager(){
        return this.autoPotManager;
    }

    public HappyGhastManager getHappyGhastManager(){
        return this.happyGhastManager;
    }
}
