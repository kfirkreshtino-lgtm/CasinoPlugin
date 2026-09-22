package com.kfir.casino.game.roulette;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.station.Station;
import com.kfir.casino.util.Tasks;
import com.kfir.casino.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One roulette table. Every player at the station shares a round, which is how real
 * roulette works and what lets several people bet into the same spin.
 *
 * <p>The betting window opens when the first bet of a round is placed and closes after
 * the configured number of seconds. Chips are taken as each bet is placed and returned
 * only on a win or on a cancelled round, so nothing is left unaccounted for.
 */
public final class RouletteRound {

    public enum Phase {
        IDLE,
        BETTING,
        SPINNING
    }

    private final CasinoPlugin plugin;
    private final Station station;
    private final Wheel wheel;

    private final Map<UUID, List<RouletteBet>> bets = new LinkedHashMap<>();
    private final Map<UUID, RouletteMenu> viewers = new HashMap<>();

    private Phase phase = Phase.IDLE;
    private int secondsLeft;
    private int lastResult = -1;

    private BukkitTask countdownTask;
    private BukkitTask spinTask;

    public RouletteRound(CasinoPlugin plugin, Station station) {
        this.plugin = plugin;
        this.station = station;
        this.wheel = new Wheel(plugin.config().rouletteWheel());
    }

    public Wheel wheel() {
        return wheel;
    }

    public Phase phase() {
        return phase;
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    public int lastResult() {
        return lastResult;
    }

    public Station station() {
        return station;
    }

    public List<RouletteBet> betsOf(UUID playerId) {
        return List.copyOf(bets.getOrDefault(playerId, List.of()));
    }

    public long wagered(UUID playerId) {
        long total = 0;
        for (RouletteBet bet : bets.getOrDefault(playerId, List.of())) {
            total += bet.amount();
        }
        return total;
    }

    public int playerCount() {
        return bets.size();
    }

    // ---------------------------------------------------------------- viewers

    public void addViewer(Player player, RouletteMenu menu) {
        viewers.put(player.getUniqueId(), menu);
    }

    public void removeViewer(UUID playerId) {
        viewers.remove(playerId);
    }

    private void refreshViewers() {
        viewers.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        for (RouletteMenu menu : new ArrayList<>(viewers.values())) {
            menu.refresh();
        }
    }

    private void tellPlayers(String message) {
        for (UUID id : bets.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                plugin.message(player, message);
            }
        }
    }

    // ------------------------------------------------------------------- bets

    /** Places a bet. Returns a MiniMessage error, or null on success. */
    public String placeBet(Player player, RouletteBet bet) {
        if (phase == Phase.SPINNING) {
            return "<red>The wheel is already spinning. Wait for the next round.</red>";
        }
        long min = plugin.config().rouletteMinBet();
        long max = plugin.config().rouletteMaxBet();
        if (bet.amount() < min) {
            return "<red>The minimum bet is " + Text.chips(min) + " chips.</red>";
        }
        if (bet.amount() > max) {
            return "<red>The maximum bet is " + Text.chips(max) + " chips.</red>";
        }
        if (!plugin.chipBank().take(player.getUniqueId(), bet.amount())) {
            return "<red>You only have " + Text.chips(plugin.chipBank().balance(player)) + " chips.</red>";
        }

        bets.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>()).add(bet);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
        plugin.message(player, "<green>Bet placed: <white>" + bet.describe(wheel) + "</white> for <white>"
                + Text.chips(bet.amount()) + "</white> chips.</green>");

        if (phase == Phase.IDLE) {
            startCountdown();
        }
        refreshViewers();
        return null;
    }

    /** Refunds and clears every bet this player has in the open round. */
    public long clearBets(UUID playerId) {
        if (phase == Phase.SPINNING) {
            return -1;
        }
        List<RouletteBet> placed = bets.remove(playerId);
        if (placed == null || placed.isEmpty()) {
            return 0;
        }
        long refund = 0;
        for (RouletteBet bet : placed) {
            refund += bet.amount();
        }
        plugin.chipBank().give(playerId, refund);
        if (bets.isEmpty()) {
            stopCountdown();
            phase = Phase.IDLE;
            secondsLeft = 0;
        }
        refreshViewers();
        return refund;
    }

    // -------------------------------------------------------------- countdown

    private void startCountdown() {
        phase = Phase.BETTING;
        secondsLeft = plugin.config().betWindowSeconds();
        stopCountdown();
        countdownTask = Tasks.timer(plugin, 20L, 20L, () -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                stopCountdown();
                spin();
                return;
            }
            if (secondsLeft <= 5 || secondsLeft == 10 || secondsLeft % 15 == 0) {
                tellPlayers("<yellow>Betting closes in <white>" + secondsLeft + "</white> seconds.</yellow>");
            }
            refreshViewers();
        });
    }

    private void stopCountdown() {
        Tasks.cancel(countdownTask);
        countdownTask = null;
    }

    // ------------------------------------------------------------------- spin

    private void spin() {
        if (bets.isEmpty()) {
            phase = Phase.IDLE;
            refreshViewers();
            return;
        }
        phase = Phase.SPINNING;
        refreshViewers();

        int result = wheel.spin();
        int totalTicks = plugin.config().spinTicks();
        final int[] elapsed = {0};

        Tasks.cancel(spinTask);
        spinTask = Tasks.timer(plugin, 0L, 2L, () -> {
            elapsed[0] += 2;
            if (elapsed[0] >= totalTicks) {
                Tasks.cancel(spinTask);
                spinTask = null;
                reveal(result);
                return;
            }
            int teaser = wheel.randomPocket();
            for (UUID id : bets.keySet()) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null) {
                    player.sendActionBar(com.kfir.casino.util.Text.mm(
                            "<gray>Spinning... </gray>" + wheel.colored(teaser)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
                }
            }
        });
    }

    private void reveal(int result) {
        lastResult = result;
        String colored = wheel.colored(result);
        for (UUID id : bets.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) {
                continue;
            }
            player.showTitle(Title.title(
                    com.kfir.casino.util.Text.mm(colored),
                    com.kfir.casino.util.Text.mm("<gray>" + describePocket(result) + "</gray>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        }
        settle(result);
    }

    private String describePocket(int pocket) {
        if (wheel.isGreen(pocket)) {
            return "Green";
        }
        String color = wheel.isRed(pocket) ? "Red" : "Black";
        String parity = pocket % 2 == 0 ? "even" : "odd";
        String half = pocket <= 18 ? "1 to 18" : "19 to 36";
        return color + ", " + parity + ", " + half;
    }

    // --------------------------------------------------------------- settling

    private void settle(int result) {
        Map<UUID, List<RouletteBet>> resolved = new LinkedHashMap<>(bets);
        bets.clear();

        for (Map.Entry<UUID, List<RouletteBet>> entry : resolved.entrySet()) {
            long staked = 0;
            long returned = 0;
            List<String> winningLines = new ArrayList<>();

            for (RouletteBet bet : entry.getValue()) {
                staked += bet.amount();
                if (bet.wins(result, wheel)) {
                    long payout = bet.payout();
                    returned += payout;
                    winningLines.add("<green>" + bet.describe(wheel) + "</green> <gray>pays</gray> <white>"
                            + Text.chips(payout) + "</white>");
                }
            }

            if (returned > 0) {
                plugin.chipBank().give(entry.getKey(), returned);
            }

            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            long net = returned - staked;
            plugin.message(player, "<gold>Result: </gold>" + wheel.colored(result)
                    + " <gray>(" + describePocket(result) + ")</gray>");
            for (String line : winningLines) {
                plugin.message(player, "  " + line);
            }
            if (net > 0) {
                plugin.message(player, "<green>You won <white>" + Text.chips(net)
                        + "</white> chips on the round.</green>");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } else if (net == 0) {
                plugin.message(player, "<yellow>You broke even on the round.</yellow>");
            } else {
                plugin.message(player, "<red>You lost <white>" + Text.chips(-net)
                        + "</white> chips on the round.</red>");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }

        phase = Phase.IDLE;
        secondsLeft = 0;
        refreshViewers();
    }

    /** Refunds every open bet. Used on reload, shutdown and when a table is removed. */
    public long cancelAndRefund() {
        stopCountdown();
        Tasks.cancel(spinTask);
        spinTask = null;
        long total = 0;
        for (Map.Entry<UUID, List<RouletteBet>> entry : bets.entrySet()) {
            long refund = 0;
            for (RouletteBet bet : entry.getValue()) {
                refund += bet.amount();
            }
            if (refund > 0) {
                plugin.chipBank().give(entry.getKey(), refund);
                total += refund;
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null) {
                    plugin.message(player, "<yellow>Round cancelled. <white>" + Text.chips(refund)
                            + "</white> chips refunded.</yellow>");
                }
            }
        }
        bets.clear();
        viewers.clear();
        phase = Phase.IDLE;
        secondsLeft = 0;
        return total;
    }
}
