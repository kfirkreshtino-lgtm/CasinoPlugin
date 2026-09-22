package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.station.Station;
import org.bukkit.entity.Player;

/**
 * Stand-in used until the real poker module registers itself.
 *
 * <p>Poker stations still exist in the building and still respond to clicks, they just
 * tell the player the game is not running yet. That keeps the casino layout final so the
 * poker module never has to move blocks around.
 */
public final class UnavailablePokerHook implements PokerHook {

    private final CasinoPlugin plugin;

    public UnavailablePokerHook(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openTable(Player player, Station station) {
        plugin.message(player, "<yellow>The poker room is not open yet. "
                + "Try blackjack or roulette in the meantime.</yellow>");
    }

    @Override
    public String moduleName() {
        return "not installed";
    }
}
