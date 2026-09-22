package com.kfir.casino.storage;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationRegistry;
import com.kfir.casino.station.StationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Reads and writes stations.yml.
 *
 * <p>Stations are stored separately from the built structure so an admin can register
 * extra tables inside a hand-built casino and keep them across restarts.
 */
public final class StationStore {

    private final CasinoPlugin plugin;
    private final File file;

    public StationStore(CasinoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stations.yml");
    }

    public void load(StationRegistry registry) {
        registry.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("stations");
        if (root == null) {
            return;
        }
        int skipped = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            StationType type = StationType.fromString(section.getString("type", ""));
            World world = resolveWorld(section.getString("world", ""));
            if (type == null || world == null) {
                skipped++;
                continue;
            }
            Location location = new Location(world,
                    section.getInt("x"), section.getInt("y"), section.getInt("z"));
            registry.add(new Station(id, type, location));
        }
        plugin.getLogger().info("Loaded " + registry.size() + " stations."
                + (skipped > 0 ? " Skipped " + skipped + " with an unknown world or type." : ""));
    }

    public void save(StationRegistry registry) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Station station : registry.all()) {
            String path = "stations." + station.id();
            yaml.set(path + ".type", station.type().name());
            yaml.set(path + ".world", station.world().getName());
            yaml.set(path + ".x", station.location().getBlockX());
            yaml.set(path + ".y", station.location().getBlockY());
            yaml.set(path + ".z", station.location().getBlockZ());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder.");
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stations.yml", ex);
        }
    }

    private World resolveWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        try {
            return Bukkit.getWorld(UUID.fromString(name));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
