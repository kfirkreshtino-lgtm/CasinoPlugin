package com.kfir.casino.station;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * One interaction point in the world.
 *
 * @param id       stable identifier, also used as the roulette round key
 * @param type     which menu opens on right-click
 * @param location the block players click
 */
public record Station(String id, StationType type, Location location) {

    public Station {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
    }

    /** Human label shown on the floating sign above the station. */
    public String label() {
        return type.displayName();
    }

    public World world() {
        return location.getWorld();
    }

    /** Map key for the registry: world, x, y and z of the block. */
    public static String key(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX()
                + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public String key() {
        return key(location);
    }
}
