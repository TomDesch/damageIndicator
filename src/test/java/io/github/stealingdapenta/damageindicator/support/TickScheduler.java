package io.github.stealingdapenta.damageindicator.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Deterministic server ticks with real task cancellation and repeat deadlines.
 */
public final class TickScheduler {

    private static final long MIN_DELAY_TICKS = 1;
    public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    public final List<Task> tasks = new ArrayList<>();
    private long tick;

    public TickScheduler() {
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> schedule(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call -> schedule(call.getArgument(0), call.getArgument(1), call.getArgument(2), 0));
    }

    private Task schedule(Plugin plugin, Runnable callback, long delay, long period) {
        Task task = new Task(tasks.size() + 1, plugin, callback, tick + Math.max(MIN_DELAY_TICKS, delay), period);
        tasks.add(task);
        return task;
    }

    public void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick++;
            for (Task task : List.copyOf(tasks)) {
                if (!task.cancelled && !task.completed && task.next <= tick) {
                    if (task.period > 0) {
                        task.next += task.period;
                    } else {
                        task.completed = true;
                    }
                    task.callback.run();
                }
            }
        }
    }

    public boolean allCancelled() {
        return tasks.stream()
                    .allMatch(Task::isCancelled);
    }

    public static final class Task implements BukkitTask {

        private final int id;
        private final Plugin plugin;
        private final Runnable callback;
        private final long period;
        private long next;
        private boolean cancelled;
        private boolean completed;

        private Task(int id, Plugin plugin, Runnable callback, long next, long period) {
            this.id = id;
            this.plugin = plugin;
            this.callback = callback;
            this.next = next;
            this.period = period;
        }

        @Override
        public int getTaskId() {
            return id;
        }

        @Override
        public @NotNull Plugin getOwner() {
            return plugin;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
