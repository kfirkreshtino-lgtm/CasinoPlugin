package com.kfir.casino.game;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.blackjack.BlackjackActionMenu;
import com.kfir.casino.game.blackjack.BlackjackBetMenu;
import com.kfir.casino.game.blackjack.BlackjackGame;
import com.kfir.casino.game.blackjack.BlackjackTableView;
import com.kfir.casino.game.roulette.RouletteMenu;
import com.kfir.casino.game.roulette.RouletteRound;
import com.kfir.casino.station.Station;
import com.kfir.casino.table.Seat;
import com.kfir.casino.table.TableLayout;
import com.kfir.casino.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every live game and makes sure no chips are ever stranded.
 *
 * <p>Blackjack is one instance per player, keyed by player id, so the same table block
 * serves any number of people at once. Roulette is one shared round per station, keyed by
 * station id, because a spin has to resolve for everyone at the table together.
 *
 * <p>Nothing in-progress is written to disk. A hand lasts seconds, so on shutdown or
 * disconnect open stakes are refunded rather than resumed. Trying to restore a half-played
 * hand across a restart is far more likely to lose chips than to save a game.
 */
public final class GameManager implements Listener {

    private final CasinoPlugin plugin;
    private final Map<UUID, BlackjackGame> blackjackGames = new HashMap<>();
    private final Map<String, RouletteRound> rouletteRounds = new HashMap<>();
    private final Map<UUID, Seat> seats = new HashMap<>();

    public GameManager(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------- blackjack

    public void openBlackjack(Player player, Station station) {
        UUID id = player.getUniqueId();
        BlackjackGame game = blackjackGames.get(id);

        if (game != null && game.isBusy() && !game.stationId().equals(station.id())) {
            plugin.message(player, "<red>Finish your hand at the other blackjack table first.</red>");
            return;
        }
        if (game == null || (!game.isBusy() && !game.stationId().equals(station.id()))) {
            if (game != null) {
                game.cancelAndRefund();
            }
            game = new BlackjackGame(plugin, player, station.id());
            blackjackGames.put(id, game);
        }

        TableLayout layout = TableLayout.forStation(station.location());
        if (game.view() == null) {
            BlackjackTableView view = new BlackjackTableView(plugin, game, player, layout);
            game.attachView(view);
            view.render();
        }
        seat(player, layout);

        // The only inventory that opens by itself is the one the player needs right now.
        switch (game.state()) {
            case BETTING -> new BlackjackBetMenu(plugin, player, game).open();
            case PLAYER_TURN -> new BlackjackActionMenu(plugin, player, game).open();
            case DEALER_TURN -> plugin.message(player,
                    "<gray>The dealer is drawing. Watch the table.</gray>");
            case FINISHED -> {
                game.reset();
                new BlackjackBetMenu(plugin, player, game).open();
            }
        }
    }

    /** Puts the player in the chair, unless they are already sitting in it. */
    private void seat(Player player, TableLayout layout) {
        Seat existing = seats.get(player.getUniqueId());
        if (existing != null) {
            if (existing.holds(player)) {
                return;
            }
            existing.release();
        }
        seats.put(player.getUniqueId(), Seat.sit(player, layout.seat()));
    }

    // --------------------------------------------------------------- roulette

    public RouletteRound round(Station station) {
        return rouletteRounds.computeIfAbsent(station.id(), key -> new RouletteRound(plugin, station));
    }

    public void openRoulette(Player player, Station station) {
        new RouletteMenu(plugin, player, round(station)).open();
    }

    // ------------------------------------------------------------------ state

    /** True while the player is mid-hand anywhere in the casino. */
    public boolean isBusy(UUID playerId) {
        BlackjackGame game = blackjackGames.get(playerId);
        if (game != null && game.isBusy()) {
            return true;
        }
        return plugin.pokerHook().isBusy(playerId);
    }

    /**
     * Pulls a player out of every game and refunds anything still at stake.
     * Returns the total chips returned.
     */
    public long leave(UUID playerId) {
        long refunded = 0;

        Seat seat = seats.remove(playerId);
        if (seat != null) {
            seat.release();
        }

        BlackjackGame game = blackjackGames.remove(playerId);
        if (game != null) {
            refunded += game.cancelAndRefund();
        }

        for (RouletteRound round : rouletteRounds.values()) {
            round.removeViewer(playerId);
            long cleared = round.clearBets(playerId);
            if (cleared > 0) {
                refunded += cleared;
            }
        }

        refunded += plugin.pokerHook().leave(playerId);
        return refunded;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Bets that are already spinning are left alone: the payout lands in the
        // offline balance, which is safer than cancelling a shared spin.
        leave(event.getPlayer().getUniqueId());
    }

    /** Called on disable and on reload. Refunds everything and clears all state. */
    public void shutdown() {
        long refunded = 0;

        for (Seat seat : new ArrayList<>(seats.values())) {
            seat.release();
        }
        seats.clear();

        for (BlackjackGame game : new ArrayList<>(blackjackGames.values())) {
            refunded += game.cancelAndRefund();
        }
        blackjackGames.clear();

        for (RouletteRound round : new ArrayList<>(rouletteRounds.values())) {
            refunded += round.cancelAndRefund();
        }
        rouletteRounds.clear();

        plugin.pokerHook().shutdown();

        if (refunded > 0) {
            plugin.getLogger().info("Refunded " + Text.chips(refunded) + " chips from games in progress.");
        }
    }

    /**
     * Clears tables nobody is at any more.
     *
     * <p>A player who dismounts and wanders off would otherwise leave their cards, floating
     * totals and chair sitting in the room. Anyone offline, or more than a few blocks from
     * their table and not mid-hand, is quietly stood up.
     */
    public void sweepAbandonedTables() {
        for (UUID id : new ArrayList<>(blackjackGames.keySet())) {
            BlackjackGame game = blackjackGames.get(id);
            if (game == null) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                leave(id);
                continue;
            }
            if (game.isBusy()) {
                continue;
            }
            Station station = plugin.stations().byId(game.stationId());
            if (station == null) {
                leave(id);
                continue;
            }
            boolean sameWorld = station.world() != null
                    && station.world().equals(player.getWorld());
            if (!sameWorld || player.getLocation().distanceSquared(station.location()) > 36) {
                leave(id);
            }
        }
    }

    public List<RouletteRound> rounds() {
        return List.copyOf(rouletteRounds.values());
    }

    public int activeBlackjackGames() {
        int count = 0;
        for (BlackjackGame game : blackjackGames.values()) {
            if (game.isBusy()) {
                count++;
            }
        }
        return count;
    }
}
