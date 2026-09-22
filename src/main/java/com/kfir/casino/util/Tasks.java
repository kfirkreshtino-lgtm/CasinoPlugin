package com.kfir.casino.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Thin wrappers around the Bukkit scheduler. Everything in this plugin runs on the main thread. */
public final class Tasks {

    private Tasks() {
    }

    public static BukkitTask later(Plugin plugin, long delayTicks, Runnable action) {
        return Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks);
    }

    public static BukkitTask timer(Plugin plugin, long delayTicks, long periodTicks, Runnable action) {
        return Bukkit.getScheduler().runTaskTimer(plugin, action, delayTicks, periodTicks);
    }

    public static BukkitTask async(Plugin plugin, Runnable action) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, action);
    }

    public static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
