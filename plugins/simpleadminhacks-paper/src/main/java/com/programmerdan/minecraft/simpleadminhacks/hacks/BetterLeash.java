package com.programmerdan.minecraft.simpleadminhacks.hacks;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.configs.BetterLeashConfig;
import com.programmerdan.minecraft.simpleadminhacks.configs.SpawnerNerfConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.BasicHackConfig;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import java.util.Objects;

public class BetterLeash extends SimpleHack<BetterLeashConfig> implements Listener {

    public BetterLeash(SimpleAdminHacks plugin, BetterLeashConfig config) {
        super(plugin, config);
    }

    @Override
    public void onEnable() {
        plugin.registerListener(this);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onUnleash(EntityUnleashEvent event) {
        if(event.getReason() != EntityUnleashEvent.UnleashReason.DISTANCE){
            return;
        }

        if(!(event.getEntity() instanceof LivingEntity entity) || !entity.isLeashed()){
            return;
        }

        Entity leashHolder = entity.getLeashHolder();
        double distance = leashHolder.getLocation().distance(entity.getLocation());
        if(distance <= config.getLeashDistance()){
            event.setCancelled(true);
        }
    }

    public static BetterLeashConfig generate(SimpleAdminHacks plugin, ConfigurationSection config) {
        return new BetterLeashConfig(plugin, config);
    }
}
