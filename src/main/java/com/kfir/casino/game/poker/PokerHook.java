package com.kfir.casino.game.poker;

import com.kfir.casino.station.Station;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Integration point for the Texas Holdem module.
 *
 * <p>This plugin owns the casino building, the stations, the chip economy and the menu
 * framework. It deliberately does not implement poker. A separate plugin implements this
 * interface and registers it, at which point every POKER station starts routing clicks
 * into that implementation.
 *
 * <p>Wiring it up from the poker plugin, in its onEnable, after CasinoPlugin has loaded:
 *
 * <pre>
 * CasinoAPI casino = Bukkit.getServicesManager().load(CasinoAPI.class);
 * casino.registerPokerHook(new MyPokerHook());
 * </pre>
 *
 * <p>Chips are moved with {@code CasinoAPI.takeChips} and {@code CasinoAPI.giveChips}.
 * Never take or hand out diamonds directly from the poker module, or buy-ins and payouts
 * will drift out of step with the cashier.
 */
public interface PokerHook {

    /** Called when a player right-clicks a poker station. Open your table UI here. */
    void openTable(Player player, Station station);

    /**
     * Called when a player quits, runs the leave command, or the server stops.
     * Refund anything the player still has at stake and return the amount refunded.
     */
    default long leave(UUID playerId) {
        return 0L;
    }

    /** True while the player is in a hand and should not be pulled into another game. */
    default boolean isBusy(UUID playerId) {
        return false;
    }

    /** Called on plugin shutdown. Cancel tasks and refund every open pot. */
    default void shutdown() {
    }

    /** Short name shown in admin output, for example on the station list. */
    default String moduleName() {
        return "none";
    }
}
