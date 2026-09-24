package com.kfir.casino.game.poker;

import com.kfir.casino.table.Dealers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Keeps the dealers at every table alive.
 *
 * <p>Marking an entity invulnerable is not enough on its own: players in creative mode can
 * still hurt invulnerable entities. Cancelling every damage event aimed at a dealer covers
 * creative players, arrows, explosions, fire and anything else.
 */
public final class DealerListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(Dealers.TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity().getScoreboardTags().contains(Dealers.TAG)) {
            event.setCancelled(true);
        }
    }
}
