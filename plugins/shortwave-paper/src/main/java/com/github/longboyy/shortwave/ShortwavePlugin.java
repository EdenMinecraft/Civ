package com.github.longboyy.creativeworlds;

import org.bukkit.event.HandlerList;
import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.civmodcore.commands.CommandManager;

public class CreativeWorldsPlugin extends ACivMod{

    private final CreativeWorldsConfig config;
    private CommandManager commandManager;

    public CreativeWorldsPlugin() {
        this.config = new CreativeWorldsConfig(this);
        //this.exilePearlListener = new ExilePearlListener(this);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if(!this.config.parse()){
            this.disable();
        }
        this.commandManager = new CommandManager(this);
        this.commandManager.init();
        //this.registerListener(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.commandManager.unregisterCommands();
        HandlerList.unregisterAll(this);
        //this.happyGhastManager.stopDamageTask();
    }
}
