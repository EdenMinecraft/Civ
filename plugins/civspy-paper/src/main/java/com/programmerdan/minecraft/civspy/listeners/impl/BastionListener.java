package com.programmerdan.minecraft.civspy.listeners.impl;

import com.programmerdan.minecraft.civspy.DataManager;
import com.programmerdan.minecraft.civspy.PointDataSample;
import com.programmerdan.minecraft.civspy.annotations.RequirePlugins;
import com.programmerdan.minecraft.civspy.listeners.ServerDataListener;
import isaac.bastion.BastionBlock;
import isaac.bastion.event.BastionCreateEvent;
import isaac.bastion.event.BastionDamageEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import vg.civcraft.mc.citadel.model.Reinforcement;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequirePlugins("Bastion")
public final class BastionListener extends ServerDataListener {

    public BastionListener(DataManager target, Logger logger, String server) {
        super(target, logger, server);
    }

    @Override
    public void shutdown() {
        // NO OP
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBastionCreate(BastionCreateEvent event){
        try{
            if(event.getBastion() == null){
                return;
            }

            Location loc = event.getBastion().getLocation();
            Chunk chunk = loc.getChunk();
            var data = new PointDataSample(
                "bastion.create",
                this.getServer(),
                loc.getWorld().getName(),
                event.getPlayer().getUniqueId(),
                chunk.getX(),
                chunk.getZ(),
                this.getBastionString(event.getBastion()));
            this.record(data);
        }catch(Exception e){
            logger.log(Level.SEVERE, "Failed to track Bastion Create Event in CivSpy", e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBastionDamage(BastionDamageEvent event){
        try{
            if(event.getBastion() == null){
                return;
            }

            if(event.getDamage() <= 0){
                return;
            }

            Location loc = event.getBastion().getLocation();
            Chunk chunk = loc.getChunk();
            var data = new PointDataSample(
                "bastion.damage",
                this.getServer(),
                loc.getWorld().getName(),
                event.getPlayer().getUniqueId(),
                chunk.getX(),
                chunk.getZ(),
                this.getBastionString(event.getBastion()),
                event.getDamage());
            this.record(data);
        }catch(Exception e){
            logger.log(Level.SEVERE, "Failed to track Bastion Damage Event in CivSpy", e);
        }

    }

    private String getBastionString(BastionBlock bastion) {
        Reinforcement r = bastion.getReinforcement();
        return String.format("{%s}|[%s/%s]",
            bastion.getLocation(),
            r.getHealth(),
            r.getType().getHealth()
        );
    }

}
