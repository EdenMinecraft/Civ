package vg.civcraft.mc.civmodcore.scheduling;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import vg.civcraft.mc.civmodcore.CivModCorePlugin;

/**
 * Scheduling facade over Paper's region-aware schedulers.
 * <p>
 * Paper ships the Folia scheduler API on every build: on regular Paper these schedulers run on the main
 * thread and never regionize, while on Folia they regionize. Delegating to them directly means one shadow
 * jar runs on both with no reflection or runtime server-type detection.
 */
public final class CivScheduler {

    private static final long MILLIS_PER_TICK = 50L;

    private static final Runnable NO_OP = () -> {
    };

    private CivScheduler() {
    }

    private static Plugin plugin() {
        return CivModCorePlugin.getInstance();
    }

    // The Folia schedulers hand the task its own ScheduledTask handle; our callers don't need it.
    private static Consumer<ScheduledTask> ignoreHandle(final Runnable task) {
        return ignored -> task.run();
    }

    // Global/region/entity scheduler delays and periods are in ticks and throw below 1.
    private static long atLeastOneTick(final long ticks) {
        return Math.max(1L, ticks);
    }

    // --- GLOBAL (main-thread on Paper, global region on Folia) ---

    public static void runGlobal(final Runnable task) {
        runGlobal(plugin(), task);
    }

    public static void runGlobal(final Plugin plugin, final Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignoreHandle(task));
    }

    public static CivTask runGlobalLater(final Runnable task, final long delayTicks) {
        return runGlobalLater(plugin(), task, delayTicks);
    }

    public static CivTask runGlobalLater(final Plugin plugin, final Runnable task, final long delayTicks) {
        return CivTask.wrap(Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, ignoreHandle(task), atLeastOneTick(delayTicks)));
    }

    public static CivTask runGlobalTimer(final Runnable task, final long delayTicks, final long periodTicks) {
        return runGlobalTimer(plugin(), task, delayTicks, periodTicks);
    }

    public static CivTask runGlobalTimer(final Plugin plugin, final Runnable task, final long delayTicks,
            final long periodTicks) {
        return CivTask.wrap(Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignoreHandle(task), atLeastOneTick(delayTicks), atLeastOneTick(periodTicks)));
    }

    // --- REGION (by location / block) ---

    public static void runRegion(final Location loc, final Runnable task) {
        Bukkit.getRegionScheduler().run(plugin(), loc, ignoreHandle(task));
    }

    public static void runRegion(final Block block, final Runnable task) {
        runRegion(block.getLocation(), task);
    }

    public static CivTask runRegionLater(final Location loc, final Runnable task, final long delayTicks) {
        return CivTask.wrap(Bukkit.getRegionScheduler()
                .runDelayed(plugin(), loc, ignoreHandle(task), atLeastOneTick(delayTicks)));
    }

    public static CivTask runRegionLater(final Block block, final Runnable task, final long delayTicks) {
        return runRegionLater(block.getLocation(), task, delayTicks);
    }

    public static CivTask runRegionTimer(final Location loc, final Runnable task, final long delayTicks,
            final long periodTicks) {
        return CivTask.wrap(Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin(), loc, ignoreHandle(task), atLeastOneTick(delayTicks),
                        atLeastOneTick(periodTicks)));
    }

    public static CivTask runRegionTimer(final Block block, final Runnable task, final long delayTicks,
            final long periodTicks) {
        return runRegionTimer(block.getLocation(), task, delayTicks, periodTicks);
    }

    // --- ENTITY (retiredFallback runs if entity removed before task) ---

    public static void runEntity(final Entity entity, final Runnable task) {
        runEntity(entity, task, NO_OP);
    }

    public static void runEntity(final Entity entity, final Runnable task, final Runnable retiredFallback) {
        entity.getScheduler().run(plugin(), ignoreHandle(task), retiredFallback);
    }

    public static CivTask runEntityLater(final Entity entity, final Runnable task, final Runnable retiredFallback,
            final long delayTicks) {
        return CivTask.wrap(entity.getScheduler()
                .runDelayed(plugin(), ignoreHandle(task), retiredFallback, atLeastOneTick(delayTicks)));
    }

    public static CivTask runEntityTimer(final Entity entity, final Runnable task, final Runnable retiredFallback,
            final long delayTicks, final long periodTicks) {
        return CivTask.wrap(entity.getScheduler()
                .runAtFixedRate(plugin(), ignoreHandle(task), retiredFallback, atLeastOneTick(delayTicks),
                        atLeastOneTick(periodTicks)));
    }

    // --- ASYNC (off main thread) ---

    public static CivTask runAsync(final Runnable task) {
        return runAsync(plugin(), task);
    }

    public static CivTask runAsync(final Plugin plugin, final Runnable task) {
        return CivTask.wrap(Bukkit.getAsyncScheduler().runNow(plugin, ignoreHandle(task)));
    }

    public static CivTask runAsyncLater(final Runnable task, final long delayTicks) {
        return CivTask.wrap(Bukkit.getAsyncScheduler()
                .runDelayed(plugin(), ignoreHandle(task), ticksToMillis(delayTicks), TimeUnit.MILLISECONDS));
    }

    public static CivTask runAsyncTimer(final Runnable task, final long delayTicks, final long periodTicks) {
        return CivTask.wrap(Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin(), ignoreHandle(task), ticksToMillis(delayTicks), ticksToMillis(periodTicks),
                        TimeUnit.MILLISECONDS));
    }

    // The async scheduler works in real time, so its delay/period are expressed as ticks-equivalent millis.
    private static long ticksToMillis(final long ticks) {
        return atLeastOneTick(ticks) * MILLIS_PER_TICK;
    }

}
