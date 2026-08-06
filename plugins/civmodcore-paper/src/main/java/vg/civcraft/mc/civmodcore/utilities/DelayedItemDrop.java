package vg.civcraft.mc.civmodcore.utilities;

import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import vg.civcraft.mc.civmodcore.scheduling.CivScheduler;

public final class DelayedItemDrop {

    /**
     * Overload for dropItemAtLocation(Location l, ItemStack is) that accepts a
     * block parameter.
     *
     * @param b  The block to drop it at
     * @param is The item to drop
     */
    public static void dropAt(Block b, ItemStack is) {
        dropAt(b.getLocation(), is);
    }

    /**
     * A better version of dropNaturally that mimics normal drop behavior.
     * <p>
     * The built-in version of Bukkit's dropItem() method places the item at the
     * block vertex which can make the item jump around. This method places the item
     * in the middle of the block location with a slight vertical velocity to mimic
     * how normal broken blocks appear.
     *
     * @param l  The location to drop the item
     * @param is The item to drop
     */
    public static void dropAt(final Location l, final ItemStack is) {
        dropAt(l, Collections.singletonList(is));
    }

    public static void dropAt(final Location l, final List<ItemStack> stacks) {
        // Schedule the item to drop 1 tick later
        CivScheduler.runRegionLater(l, () -> {
            // Location.add mutates in place, so clone once to center the drop without
            // accumulating the offset per stack or mutating the caller's Location.
            final Location dropLoc = l.clone().add(0.5, 0.5, 0.5);
            for (ItemStack is : stacks) {
                dropLoc.getWorld().dropItem(dropLoc, is).setVelocity(new Vector(0, 0.05, 0));
            }
        }, 1L);
    }

}
