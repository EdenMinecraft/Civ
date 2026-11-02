package com.programmerdan.minecraft.civspy.listeners.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Shelf;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.programmerdan.minecraft.civspy.DataManager;
import com.programmerdan.minecraft.civspy.DataSample;
import com.programmerdan.minecraft.civspy.PointDataSample;
import com.programmerdan.minecraft.civspy.listeners.ServerDataListener;

/**
 * Records players using shelves to swap items with their hotbar.
 * Generates: <code>player.shelf.use</code> stat_key data, with the shelf's material in the string value.
 */
public final class ShelfListener extends ServerDataListener {

    public ShelfListener(DataManager target, Logger logger, String server) {
        super(target, logger, server);
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void shelfListen(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block == null || !(block.getBlockData() instanceof Shelf)) {
                return;
            }
            // Shelves only react to the main hand, with any item or none
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Player player = event.getPlayer();
            // Sneaking with anything in either hand uses the item, not the shelf
            if (player.isSneaking() && (!player.getInventory().getItemInMainHand().isEmpty()
                || !player.getInventory().getItemInOffHand().isEmpty())) {
                return;
            }
            Chunk chunk = block.getChunk();

            DataSample shelfUse = new PointDataSample("player.shelf.use", this.getServer(),
                chunk.getWorld().getName(), player.getUniqueId(), chunk.getX(), chunk.getZ(),
                block.getType().name());
            this.record(shelfUse);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to spy a shelf use", e);
        }
    }
}
