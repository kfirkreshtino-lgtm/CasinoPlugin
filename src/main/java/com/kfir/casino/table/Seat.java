package com.kfir.casino.table;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

/**
 * Sits a player down at a table.
 *
 * <p>An invisible armour stand is spawned at the seat and the player rides it, which is
 * what puts them in a sitting pose at the right height and stops them drifting away
 * mid-hand. Standing up removes the stand.
 */
public final class Seat {

    private final ArmorStand stand;

    private Seat(ArmorStand stand) {
        this.stand = stand;
    }

    /** Seats the player, facing the table. */
    public static Seat sit(Player player, Location where) {
        ArmorStand stand = where.getWorld().spawn(where, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setMarker(false);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setCanPickupItems(false);
        });
        stand.addPassenger(player);
        return new Seat(stand);
    }

    /** Whether this seat still holds the given player. */
    public boolean holds(Player player) {
        return !stand.isDead() && stand.getPassengers().contains(player);
    }

    /** Stands the player up and removes the seat. */
    public void release() {
        if (!stand.isDead()) {
            stand.eject();
            stand.remove();
        }
    }
}
