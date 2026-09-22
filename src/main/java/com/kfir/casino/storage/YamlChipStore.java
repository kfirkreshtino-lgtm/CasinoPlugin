package com.kfir.casino.storage;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.util.Tasks;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Chip balances in chips.yml.
 *
 * <p>Balances live in an in-memory map that only the main thread mutates. Saving copies
 * that map on the main thread and writes the copy asynchronously, so the file write never
 * races with gameplay.
 */
public final class YamlChipStore implements ChipStore {

    private final CasinoPlugin plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    public YamlChipStore(CasinoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chips.yml");
    }

    public void load() {
        balances.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), Math.max(0L, yaml.getLong(key)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed chip entry: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + balances.size() + " chip balances.");
    }

    @Override
    public long get(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    @Override
    public void set(UUID playerId, long chips) {
        balances.put(playerId, Math.max(0L, chips));
        dirty.set(true);
    }

    @Override
    public void saveAsync() {
        if (!dirty.getAndSet(false)) {
            return;
        }
        Map<UUID, Long> snapshot = new HashMap<>(balances);
        Tasks.async(plugin, () -> write(snapshot));
    }

    @Override
    public void saveNow() {
        dirty.set(false);
        write(new HashMap<>(balances));
    }

    private void write(Map<UUID, Long> snapshot) {
        synchronized (writeLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
                if (entry.getValue() > 0) {
                    yaml.set(entry.getKey().toString(), entry.getValue());
                }
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    plugin.getLogger().warning("Could not create the plugin data folder.");
                }
                yaml.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save chips.yml", ex);
                dirty.set(true);
            }
        }
    }
}
