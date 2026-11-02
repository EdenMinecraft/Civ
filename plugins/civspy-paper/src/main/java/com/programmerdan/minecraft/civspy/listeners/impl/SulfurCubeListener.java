package com.programmerdan.minecraft.civspy.listeners.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.civspy.DataManager;
import com.programmerdan.minecraft.civspy.DataSample;
import com.programmerdan.minecraft.civspy.PointDataSample;
import com.programmerdan.minecraft.civspy.listeners.ServerDataListener;
import com.programmerdan.minecraft.civspy.util.ItemStackToString;

import io.papermc.paper.event.entity.SulfurCubeSwallowItemEvent;

/**
 * Records players feeding items to sulfur cubes.
 * Generates: <code>player.sulfur_cube.feed</code> stat_key data, with the swallowed item serialized in the string
 * value and its amount in the number value.
 */
public final class SulfurCubeListener extends ServerDataListener {

    public SulfurCubeListener(DataManager target, Logger logger, String server) {
        super(target, logger, server);
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void feedListen(SulfurCubeSwallowItemEvent event) {
        try {
            Player player = event.getPlayer();
            ItemStack swallowed = event.getNewItem();
            if (player == null || swallowed == null || swallowed.isEmpty()) return;
            Chunk chunk = event.getEntity().getLocation().getChunk();

            ItemStack stack = swallowed.clone();
            stack.setAmount(1);
            DataSample feed = new PointDataSample("player.sulfur_cube.feed", this.getServer(),
                chunk.getWorld().getName(), player.getUniqueId(), chunk.getX(), chunk.getZ(),
                ItemStackToString.toString(stack), swallowed.getAmount());
            this.record(feed);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to spy a sulfur cube feed", e);
        }
    }
}
