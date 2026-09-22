package com.kfir.casino.structure;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.station.Station;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Builds and removes the casino, and keeps enough information in structure.yml to put the
 * terrain back exactly as it was.
 *
 * <p>Design note: remove is a real undo, not a fill with air. Before the first block is
 * placed every non-air block in the footprint is written down as block data. Removing
 * clears the footprint and then replays that snapshot, so an admin who builds on a hillside
 * gets the hillside back.
 */
public final class StructureService {

    private final CasinoPlugin plugin;
    private final StructureBuilder builder;
    private final File file;

    public StructureService(CasinoPlugin plugin, StructureBuilder builder) {
        this.plugin = plugin;
        this.builder = builder;
        this.file = new File(plugin.getDataFolder(), "structure.yml");
    }

    public boolean isBuilt() {
        return file.exists();
    }

    /** Builds at the world spawn plus the configured offset. Returns an error, or null. */
    public String build(World world) {
        if (isBuilt()) {
            return "<red>A casino is already built. Run <white>/casino remove</white> first.</red>";
        }

        Vector offset = plugin.config().structureOffset();
        Location spawn = world.getSpawnLocation();
        Location origin = new Location(world,
                spawn.getBlockX() + offset.getBlockX(),
                spawn.getBlockY() + offset.getBlockY(),
                spawn.getBlockZ() + offset.getBlockZ());

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - builder.height();
        if (origin.getBlockY() < minY || origin.getBlockY() > maxY) {
            return "<red>The build would fall outside the world height. Adjust the structure offset.</red>";
        }

        Map<String, String> snapshot = snapshot(world, origin);
        BuildResult result = builder.build(origin, plugin.config().spawnLabels(), "");

        for (Station station : result.stations()) {
            plugin.stations().add(station);
        }
        plugin.stationStore().save(plugin.stations());

        save(world, origin, snapshot, result);
        return null;
    }

    /** Restores the terrain and unregisters the stations this build created. */
    public String remove() {
        if (!isBuilt()) {
            return "<red>There is no casino to remove.</red>";
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        World world = Bukkit.getWorld(yaml.getString("world", ""));
        if (world == null) {
            return "<red>The world this casino was built in is not loaded.</red>";
        }

        despawnMarkers(yaml.getStringList("markers"));
        unregisterStations(yaml.getStringList("stations"));
        restoreTerrain(world, yaml);

        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete structure.yml. Delete it by hand.");
        }
        plugin.stationStore().save(plugin.stations());
        return null;
    }

    // --------------------------------------------------------------- snapshot

    /** Records every non-air block in the footprint so removal can replay it. */
    private Map<String, String> snapshot(World world, Location origin) {
        Map<String, String> blocks = new HashMap<>();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int x = 0; x < builder.width(); x++) {
            for (int y = 0; y < builder.height(); y++) {
                for (int z = 0; z < builder.depth(); z++) {
                    Block block = world.getBlockAt(ox + x, oy + y, oz + z);
                    if (block.getType() != Material.AIR) {
                        blocks.put(x + "," + y + "," + z, block.getBlockData().getAsString());
                    }
                }
            }
        }
        return blocks;
    }

    private void restoreTerrain(World world, YamlConfiguration yaml) {
        int ox = yaml.getInt("origin.x");
        int oy = yaml.getInt("origin.y");
        int oz = yaml.getInt("origin.z");
        int width = yaml.getInt("size.width", builder.width());
        int height = yaml.getInt("size.height", builder.height());
        int depth = yaml.getInt("size.depth", builder.depth());

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    world.getBlockAt(ox + x, oy + y, oz + z).setType(Material.AIR, false);
                }
            }
        }

        int failed = 0;
        for (String entry : yaml.getStringList("blocks")) {
            int split = entry.indexOf('=');
            if (split < 0) {
                failed++;
                continue;
            }
            String[] parts = entry.substring(0, split).split(",");
            if (parts.length != 3) {
                failed++;
                continue;
            }
            try {
                BlockData data = Bukkit.createBlockData(entry.substring(split + 1));
                world.getBlockAt(
                        ox + Integer.parseInt(parts[0]),
                        oy + Integer.parseInt(parts[1]),
                        oz + Integer.parseInt(parts[2])).setBlockData(data, false);
            } catch (IllegalArgumentException ex) {
                failed++;
            }
        }
        if (failed > 0) {
            plugin.getLogger().warning("Could not restore " + failed + " saved blocks; they were left as air.");
        }
    }

    private void despawnMarkers(List<String> markerIds) {
        for (String id : markerIds) {
            try {
                Entity entity = Bukkit.getEntity(UUID.fromString(id));
                if (entity != null) {
                    entity.remove();
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed id in the file; nothing to remove.
            }
        }
    }

    private void unregisterStations(List<String> stationIds) {
        for (String id : stationIds) {
            Station station = plugin.stations().byId(id);
            if (station != null) {
                plugin.stations().removeAt(station.location());
            }
        }
    }

    // ------------------------------------------------------------------- save

    private void save(World world, Location origin, Map<String, String> snapshot, BuildResult result) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", world.getName());
        yaml.set("origin.x", origin.getBlockX());
        yaml.set("origin.y", origin.getBlockY());
        yaml.set("origin.z", origin.getBlockZ());
        yaml.set("size.width", builder.width());
        yaml.set("size.height", builder.height());
        yaml.set("size.depth", builder.depth());

        List<String> blocks = new ArrayList<>(snapshot.size());
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            blocks.add(entry.getKey() + "=" + entry.getValue());
        }
        yaml.set("blocks", blocks);

        List<String> stationIds = new ArrayList<>();
        for (Station station : result.stations()) {
            stationIds.add(station.id());
        }
        yaml.set("stations", stationIds);

        List<String> markers = new ArrayList<>();
        for (UUID id : result.markers()) {
            markers.add(id.toString());
        }
        yaml.set("markers", markers);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder.");
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save structure.yml. Remove will not work.", ex);
        }
    }

    public StructureBuilder builder() {
        return builder;
    }
}
