package vg.civcraft.mc.civmodcore.utilities.cooldowns;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.java.JavaPlugin;
import vg.civcraft.mc.civmodcore.scheduling.CivScheduler;

/**
 * Cooldown implementation that keeps track of objects in ticks. The value given in the constructor is assumed to be in
 * ticks and time stamps are stored as a tick timestamp, which is powered by an internal counter that's incremented
 * every tick
 *
 * @param <E> Object that cooldowns are assigned to
 * @author Maxopoly
 */
public class TickCoolDownHandler<E> implements ICoolDownHandler<E> {

    private Map<E, Long> cds;

    private long cooldown;

    // Incremented on the global region thread but read by cooldown checks on region/entity threads
    private final AtomicLong tickCounter = new AtomicLong();

    public TickCoolDownHandler(JavaPlugin executingPlugin, long cooldown) {
        this.cooldown = cooldown;
        cds = new HashMap<>();
        CivScheduler.runGlobalTimer(executingPlugin, tickCounter::incrementAndGet, 1L, 1L);
    }

    @Override
    public void putOnCoolDown(E e) {
        cds.put(e, tickCounter.get());
    }

    @Override
    public boolean onCoolDown(E e) {
        Long lastUsed = cds.get(e);
        if (lastUsed == null || (tickCounter.get() - lastUsed) > cooldown) {
            return false;
        }
        return true;
    }

    @Override
    public long getRemainingCoolDown(E e) {
        Long lastUsed = cds.get(e);
        if (lastUsed == null) {
            return 0L;
        }
        long leftOver = tickCounter.get() - lastUsed;
        if (leftOver < cooldown) {
            return cooldown - leftOver;
        }
        return 0L;
    }

    @Override
    public long getTotalCoolDown() {
        return cooldown;
    }

    @Override
    public void removeCooldown(E e) {
        cds.remove(e);
    }

}
