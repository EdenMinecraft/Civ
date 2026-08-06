package vg.civcraft.mc.civmodcore.scheduling;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CivTaskTests {

    @Test
    public void cancelForwardsToScheduledTask() {
        FakeScheduledTask fake = new FakeScheduledTask();
        CivTask task = CivTask.wrap(fake);

        Assertions.assertFalse(task.isCancelled());

        task.cancel();

        Assertions.assertTrue(fake.cancelled);
        Assertions.assertTrue(task.isCancelled());
    }

    @Test
    public void isCancelledReflectsUnderlyingState() {
        FakeScheduledTask fake = new FakeScheduledTask();
        CivTask task = CivTask.wrap(fake);

        Assertions.assertFalse(task.isCancelled());
        fake.cancelled = true;
        Assertions.assertTrue(task.isCancelled());
    }

    private static final class FakeScheduledTask implements ScheduledTask {

        private boolean cancelled;

        @Override
        public Plugin getOwningPlugin() {
            return null;
        }

        @Override
        public boolean isRepeatingTask() {
            return false;
        }

        @Override
        public CancelledState cancel() {
            this.cancelled = true;
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            return this.cancelled ? ExecutionState.CANCELLED : ExecutionState.IDLE;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }
    }

}
