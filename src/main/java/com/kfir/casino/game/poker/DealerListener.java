package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.gui.CashierMenu;
import com.kfir.casino.table.Dealers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps the dealers at every table alive.
 *
 * <p>Marking an entity invulnerable is not enough on its own: players in creative mode can
 * still hurt invulnerable entities. Cancelling every damage event aimed at a dealer covers
 * creative players, arrows, explosions, fire and anything else.
 *
 * <p>Right-clicking the cashier opens the cashier menu, the same as clicking the counter.
 */
public final class DealerListener implements Listener {

    private final CasinoPlugin plugin;

    public DealerListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getRightClicked().getScoreboardTags().contains(Dealers.CASHIER_TAG)) {
            return;
        }
        event.setCancelled(true);
        new CashierMenu(plugin, event.getPlayer()).open();
    }

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
