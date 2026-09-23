package com.kfir.casino.station;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.poker.PokerLayout;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Turns a right-click on a registered block into a menu.
 *
 * <p>Design note: pressure plates were rejected because players trigger them by walking
 * past. A deliberate right-click on the table block is unambiguous and needs no extra entity.
 */
public final class StationListener implements Listener {

    private final CasinoPlugin plugin;

    public StationListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Station station = plugin.stations().at(block.getLocation());
        if (station == null) {
            station = pokerTableAt(block);
        }
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        plugin.openStation(event.getPlayer(), station);
    }

    /** A poker table is big, so any of its felt, rail or chairs counts as clicking it. */
    private Station pokerTableAt(Block block) {
        for (Station station : plugin.stations().ofType(StationType.POKER)) {
            if (station.world() != null && PokerLayout.forStation(station.location()).covers(block)) {
                return station;
            }
        }
        return null;
    }

    /** Stops a registered table from being mined out from under an active game. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Station station = plugin.stations().at(event.getBlock().getLocation());
        if (station == null) {
            return;
        }
        if (event.getPlayer().hasPermission("casino.admin") && event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        plugin.message(event.getPlayer(), "<red>That is a casino station. Sneak while breaking to remove it.</red>");
    }
}
