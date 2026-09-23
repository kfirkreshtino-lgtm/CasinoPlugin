package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.station.Station;
import com.kfir.casino.util.Tasks;
import com.kfir.casino.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The built-in Texas Holdem, one {@link PokerTable} per poker station.
 *
 * <p>A table is created the first time someone clicks it and packed away a minute after
 * the last player stands up, so an empty poker room has nothing floating over it. One
 * timer ticks every table once a second.
 */
public final class HoldemPokerHook implements PokerHook {

    private final CasinoPlugin plugin;
    private final Map<String, PokerTable> tables = new HashMap<>();
    private BukkitTask ticker;

    public HoldemPokerHook(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openTable(Player player, Station station) {
        UUID id = player.getUniqueId();
        for (PokerTable table : tables.values()) {
            if (table.isSeated(id) && !table.station().id().equals(station.id())) {
                plugin.message(player, "<red>You are sitting at another poker table. "
                        + "Use <white>/casino leave</white> first.</red>");
                return;
            }
        }
        if (plugin.games().inBlackjackHand(id)) {
            plugin.message(player, "<red>Finish your blackjack hand first.</red>");
            return;
        }
        long back = plugin.games().leaveBlackjack(id);
        if (back > 0) {
            plugin.message(player, "<gray>You left the blackjack table. <white>" + Text.chips(back)
                    + "</white> chips returned.</gray>");
        }

        PokerTable table = tables.computeIfAbsent(station.id(), key -> new PokerTable(plugin, station));
        startTicker();
        table.open(player);
    }

    @Override
    public long leave(UUID playerId) {
        long refunded = 0;
        for (PokerTable table : new ArrayList<>(tables.values())) {
            refunded += table.leave(playerId);
        }
        return refunded;
    }

    @Override
    public boolean isBusy(UUID playerId) {
        for (PokerTable table : tables.values()) {
            if (table.isInHand(playerId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        Tasks.cancel(ticker);
        ticker = null;
        long refunded = 0;
        for (PokerTable table : tables.values()) {
            refunded += table.shutdown();
        }
        tables.clear();
        if (refunded > 0) {
            plugin.getLogger().info("Returned " + Text.chips(refunded) + " chips from poker tables.");
        }
    }

    @Override
    public String moduleName() {
        return "built-in Texas Hold'em";
    }

    private void startTicker() {
        if (ticker == null) {
            ticker = Tasks.timer(plugin, 20L, 20L, this::tick);
        }
    }

    private void tick() {
        for (PokerTable table : new ArrayList<>(tables.values())) {
            table.tick();
        }
        tables.values().removeIf(table -> {
            if (table.isAbandoned()) {
                table.shutdown();
                return true;
            }
            return false;
        });
        if (tables.isEmpty()) {
            Tasks.cancel(ticker);
            ticker = null;
        }
    }
}
