package vg.civcraft.mc.civmodcore.inventory.gui;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vg.civcraft.mc.civmodcore.scheduling.CivScheduler;
import vg.civcraft.mc.civmodcore.scheduling.CivTask;

public class AnimatedClickable extends IClickable {

    private List<ItemStack> items;
    private long timing;
    private int currentPos;

    public AnimatedClickable(List<ItemStack> stacks, long timing) {
        if (stacks.isEmpty()) {
            throw new IllegalArgumentException("Can't create blinking clickable with empty item list");
        }
        this.items = stacks;
        this.timing = timing;
        this.currentPos = 0;
    }

    public ItemStack getNext() {
        if (++currentPos == items.size()) {
            currentPos = 0;
        }
        return items.get(currentPos);
    }

    @Override
    public void clicked(Player p) {
    }

    @Override
    public ItemStack getItemStack() {
        return items.get(0);
    }

    @Override
    public void addedToInventory(final ClickableInventory inv, final int slot) {
        // Schedule swapping out of item
        CivTask task = CivScheduler.runGlobalTimer(() -> inv.setItem(getNext(), slot), timing, timing);
        inv.registerTask(task);
    }

    /**
     * @return How often this instance will switch it's item representation
     */
    public long getTiming() {
        return timing;
    }

}
