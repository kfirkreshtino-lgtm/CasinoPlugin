package com.kfir.casino.api;

import com.kfir.casino.game.poker.PokerHook;
import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The surface other plugins are allowed to use.
 *
 * <p>Registered with the Bukkit services manager on enable, so a companion plugin gets it
 * with {@code Bukkit.getServicesManager().load(CasinoAPI.class)}. Everything a poker module
 * needs is here: chips, stations and the hook registration.
 */
public interface CasinoAPI {

    /** Current chip balance. Zero for a player who has never bought in. */
    long getChips(UUID playerId);

    /** Removes chips. Returns false and changes nothing when the balance is too low. */
    boolean takeChips(UUID playerId, long chips);

    /** Adds chips. */
    void giveChips(UUID playerId, long chips);

    /** Chips one diamond buys at the cashier, from config.yml. Chips are only bought with diamonds. */
    int chipsPerDiamond();

    /** Every registered station of one type, for example all poker tables. */
    List<Station> stations(StationType type);

    /** Replaces the poker implementation. Pass null to go back to the built-in Texas Hold'em. */
    void registerPokerHook(PokerHook hook);

    /** True when the player is mid-hand in any casino game, poker included. */
    boolean isBusy(UUID playerId);

    /** Sends a prefixed MiniMessage line to a player. */
    void message(Player player, String miniMessage);
}
