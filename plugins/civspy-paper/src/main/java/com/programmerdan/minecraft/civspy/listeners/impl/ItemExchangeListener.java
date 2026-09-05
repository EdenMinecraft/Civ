package com.programmerdan.minecraft.civspy.listeners.impl;

import com.programmerdan.minecraft.civspy.DataManager;
import com.programmerdan.minecraft.civspy.DataSample;
import com.programmerdan.minecraft.civspy.PointDataSample;
import com.programmerdan.minecraft.civspy.listeners.ServerDataListener;
import com.programmerdan.minecraft.civspy.util.ItemStackToString;
import com.untamedears.itemexchange.events.SuccessfulPurchaseEvent;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import vg.civcraft.mc.civmodcore.inventory.items.ItemMap;
import java.util.Arrays;
import java.util.logging.Logger;

public class ItemExchangeListener extends ServerDataListener {

    public ItemExchangeListener(DataManager target, Logger logger, String server) {
        super(target, logger, server);
    }

    @Override
    public void shutdown() {
        // NO OP
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPurchase(SuccessfulPurchaseEvent event){
        Player player = event.getPurchaser();
        Block shopBlock = event.getTrade().getBlock();
        Chunk chunk = shopBlock.getChunk();

        DataSample sample = new PointDataSample("itemexchange.purchase", this.getServer(),
            shopBlock.getWorld().getName(), player.getUniqueId(), chunk.getX(), chunk.getZ(),
            purchaseToString(event.getPaymentItems(), event.getPurchasedItems()), 1);
        this.record(sample);
    }

    private static String purchaseToString(ItemStack[] payment, ItemStack[] output){
        StringBuilder builder = new StringBuilder();
        if(payment.length != 0){
            for(ItemStack item : payment){
                builder.append(ItemStackToString.toString(item)).append("; ");
            }
        }else{
            builder.append("Nothing ");
        }
        builder.append(">> ");

        if(output.length != 0){
            for(ItemStack item : output){
                builder.append(ItemStackToString.toString(item)).append("; ");
            }
        }else{
            builder.append("Nothing");
        }
        return builder.toString();
    }
}
