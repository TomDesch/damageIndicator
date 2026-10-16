package io.github.stealingdapenta.damageindicator.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class TickSchedulerTest {

    private final TickScheduler clock = new TickScheduler();
    private final Plugin plugin = mock(Plugin.class);

    @Test
    void repeatingTaskRespectsDelayPeriodAndCancellation() {
        AtomicInteger calls = new AtomicInteger();
        BukkitTask task = clock.scheduler.runTaskTimer(plugin, calls::incrementAndGet, 2, 3);
        clock.advance(1);
        assertEquals(0, calls.get());
        clock.advance(1);
        assertEquals(1, calls.get());
        clock.advance(3);
        assertEquals(2, calls.get());
        task.cancel();
        clock.advance(10);
        assertEquals(2, calls.get());
        assertTrue(task.isCancelled());
    }

    @Test
    void completedOneShotIsNotReportedAsExplicitlyCancelled() {
        AtomicInteger calls = new AtomicInteger();
        BukkitTask task = clock.scheduler.runTaskLater(plugin, calls::incrementAndGet, 0);
        assertEquals(0, calls.get());
        clock.advance(10);
        assertEquals(1, calls.get());
        assertFalse(task.isCancelled());
    }

    @Test
    void tasksCreatedDuringACallbackRunOnTheNextTick() {
        AtomicInteger calls = new AtomicInteger();
        clock.scheduler.runTaskLater(plugin, () -> clock.scheduler.runTaskLater(plugin, calls::incrementAndGet, 0), 0);
        clock.advance(1);
        assertEquals(0, calls.get());
        clock.advance(1);
        assertEquals(1, calls.get());
    }
}
