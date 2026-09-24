package com.kfir.casino.table;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * The dealers standing at the casino tables.
 *
 * <p>The building spawns them and tags them, and the games find them again by that tag,
 * so nothing has to remember which entity belongs to which table across restarts.
 */
public final class Dealers {

    /** Scoreboard tag on every dealer the building spawns. */
    public static final String TAG = "casino_dealer";
    /** Extra tag on the cashier, whose right-click opens the cashier. */
    public static final String CASHIER_TAG = "casino_cashier";

    private Dealers() {
    }

    /** The dealer nearest to a spot, if one stands within the radius. */
    public static LivingEntity near(Location spot, double radius) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : spot.getNearbyEntities(radius, 3, radius)) {
            if (entity instanceof LivingEntity living && entity.getScoreboardTags().contains(TAG)) {
                double distance = entity.getLocation().distanceSquared(spot);
                if (distance < best) {
                    best = distance;
                    nearest = living;
                }
            }
        }
        return nearest;
    }

    /**
     * Makes a table's dealer reach out, as if dealing. Pass the dealer found last time,
     * which is reused while it is still there; the one used is returned for next time.
     */
    public static LivingEntity gesture(LivingEntity known, Location table, double radius) {
        LivingEntity dealer = known;
        if (dealer == null || !dealer.isValid()) {
            dealer = near(table, radius);
        }
        if (dealer != null) {
            dealer.swingMainHand();
        }
        return dealer;
    }
}
