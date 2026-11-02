package com.programmerdan.minecraft.civspy.listeners.impl;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;

import com.programmerdan.minecraft.civspy.DataManager;
import com.programmerdan.minecraft.civspy.DataSample;
import com.programmerdan.minecraft.civspy.PointDataSample;
import com.programmerdan.minecraft.civspy.listeners.ServerDataListener;

/**
 * Records golems.
 * Generates: <code>entity.construct.TYPE</code> stat_key data, where TYPE is the EntityType.name() built.
 * The builder is the player who placed the final head block.
 */
public final class ConstructListener extends ServerDataListener {

    // the construct spawns while the head's BlockPlaceEvent is still being handled, in the same tick
    private Location lastHead;
    private UUID lastHeadPlacer;
    private int lastHeadTick;

    public ConstructListener(DataManager target, Logger logger, String server) {
        super(target, logger, server);
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberHeadPlacer(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type != Material.CARVED_PUMPKIN && type != Material.JACK_O_LANTERN
            && type != Material.WITHER_SKELETON_SKULL && type != Material.WITHER_SKELETON_WALL_SKULL) {
            return;
        }
        lastHead = event.getBlockPlaced().getLocation();
        lastHeadPlacer = event.getPlayer().getUniqueId();
        lastHeadTick = Bukkit.getCurrentTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void constructListen(CreatureSpawnEvent event) {
        try {
            switch (event.getSpawnReason()) {
                case BUILD_SNOWMAN, BUILD_IRONGOLEM, BUILD_COPPERGOLEM, BUILD_WITHER -> {
                }
                default -> {
                    return;
                }
            }
            Location location = event.getLocation();
            Chunk chunk = location.getChunk();

            DataSample construct = new PointDataSample("entity.construct." + event.getEntityType().name(),
                this.getServer(), chunk.getWorld().getName(), getBuilder(location), chunk.getX(), chunk.getZ(), 1);
            this.record(construct);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to spy a construct event", e);
        }
    }

    /**
     * Constructs spawn somewhere inside their pattern, so the head only has to be close by.
     */
    private UUID getBuilder(Location spawn) {
        if (lastHead == null || lastHeadTick != Bukkit.getCurrentTick() || lastHead.getWorld() != spawn.getWorld()
            || lastHead.distanceSquared(spawn) > 9) {
            return null;
        }
        return lastHeadPlacer;
    }
}
