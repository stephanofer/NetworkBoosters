package com.stephanofer.networkboosters.event;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PaperThreadExecutor {

    private final Plugin plugin;

    public PaperThreadExecutor(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public boolean execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!this.plugin.isEnabled()) {
            return false;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return true;
        }
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
