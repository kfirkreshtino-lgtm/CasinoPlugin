package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoConfig;
import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.card.Card;
import com.kfir.casino.game.card.Deck;
import com.kfir.casino.station.Station;
import com.kfir.casino.table.Seat;
import com.kfir.casino.util.Tasks;
import com.kfir.casino.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One poker table in the world: its six seats, the chips on it and the hand being played.
 *
 * <p>The rules live in {@link HoldemHand}. This class decides when things happen: it
 * waits until enough players are seated, counts down, starts hands, gives each player a
 * limited time to act, pauses between streets so the table can be read, and pays out.
 *
 * <p>Chips a player sits down with leave their casino balance and are only on the table.
 * Standing up, walking away, disconnecting or the server stopping all return what is left
 * in front of them. Leaving mid-hand folds the hand; chips already in the pot stay there.
 */
public final class PokerTable {

    enum State {
        /** Fewer players than needed. */
        WAITING,
        /** Enough players; the next hand starts when the countdown ends. */
        COUNTDOWN,
        IN_HAND,
        /** A hand has been paid out and is still on show. */
        BETWEEN_HANDS
    }

    /** What a player chose in the action menu. */
    enum Move {
        FOLD, CHECK_OR_CALL, RAISE
    }

    /** Pause between the end of a betting round and the next cards. */
    private static final int STREET_PAUSE_TICKS = 25;
    /** How long the result stays on the table after a showdown, and after everyone folds. */
    private static final int SHOWDOWN_TICKS = 120;
    private static final int FOLDED_OUT_TICKS = 50;
    /** Short delay before the action menu opens, so the player sees the last move first. */
    private static final int MENU_DELAY_TICKS = 10;
    /** Players further than this from the table are stood up. */
    private static final double LEAVE_DISTANCE_SQUARED = 10 * 10;
    /** An empty table is packed away after this long, which leaves time to pick a buy-in. */
    private static final int EMPTY_SECONDS_BEFORE_CLOSING = 60;

    private static final class TableSeat {
        final UUID id;
        final String name;
        long stack;
        Seat chair;

        TableSeat(UUID id, String name, long stack) {
            this.id = id;
            this.name = name;
            this.stack = stack;
        }
    }

    private final CasinoPlugin plugin;
    private final Station station;
    private final PokerLayout layout;
    private final PokerTableView view;
    private final TableSeat[] seats = new TableSeat[PokerLayout.SEATS];

    private State state = State.WAITING;
    private int countdown;
    private int buttonSeat = -1;
    private HoldemHand hand;
    private HandPlayer lastAsked;
    private int turn;
    private int turnSecondsLeft;
    private String result = "";
    private BukkitTask pending;
    private int emptySeconds;
    private boolean closed;

    PokerTable(CasinoPlugin plugin, Station station) {
        this.plugin = plugin;
        this.station = station;
        this.layout = PokerLayout.forStation(station.location());
        this.view = new PokerTableView(plugin, layout);
        render();
    }

    // ---------------------------------------------------------------- seating

    /** A right-click on the table: sit down, get back in the chair, or open the menu. */
    void open(Player player) {
        int index = seatOf(player.getUniqueId());
        if (index < 0) {
            if (freeSeat() < 0) {
                plugin.message(player, "<red>This table is full.</red>");
                return;
            }
            long min = config().pokerMinBuyIn();
            if (plugin.chipBank().balance(player) < min) {
                plugin.message(player, "<red>You need at least <white>" + Text.chips(min)
                        + "</white> chips to sit down. Buy chips with diamonds at the cashier.</red>");
                return;
            }
            new PokerBuyInMenu(plugin, player, this).open();
            return;
        }
        TableSeat seat = seats[index];
        if (seat.chair == null || !seat.chair.holds(player)) {
            if (seat.chair != null) {
                seat.chair.release();
            }
            seat.chair = Seat.sit(player, layout.chair(index));
        }
        new PokerMenu(plugin, player, this).open();
    }

    /** Takes the buy-in from the player's balance and seats them in the first free chair. */
    void sit(Player player, long buyIn) {
        UUID id = player.getUniqueId();
        if (closed) {
            plugin.message(player, "<red>That table was packed away. Right-click it again.</red>");
            return;
        }
        if (seatOf(id) >= 0) {
            return;
        }
        int index = freeSeat();
        if (index < 0) {
            plugin.message(player, "<red>Someone took the last seat.</red>");
            return;
        }
        CasinoConfig config = config();
        long amount = Math.max(config.pokerMinBuyIn(), Math.min(config.pokerMaxBuyIn(), buyIn));
        if (!plugin.chipBank().take(id, amount)) {
            plugin.message(player, "<red>You do not have " + Text.chips(amount) + " chips.</red>");
            return;
        }
        TableSeat seat = new TableSeat(id, player.getName(), amount);
        seat.chair = Seat.sit(player, layout.chair(index));
        seats[index] = seat;

        broadcast("<aqua>" + player.getName() + "</aqua> <gray>sat down with <white>"
                + Text.chips(amount) + "</white> chips.</gray>");
        if (state == State.IN_HAND || state == State.BETWEEN_HANDS) {
            plugin.message(player, "<gray>A hand is being played. You are dealt in from the next one.</gray>");
        }
        updateWaiting();
        render();
    }

    /**
     * Stands a player up and returns what is left in front of them to their balance.
     * Mid-hand this folds their cards first. Returns the chips returned.
     */
    long leave(UUID id) {
        int index = seatOf(id);
        if (index < 0) {
            return 0;
        }
        TableSeat seat = seats[index];
        long refund = seat.stack;

        HandPlayer inHand = hand != null ? hand.player(id) : null;
        boolean handRunning = inHand != null && hand.phase() != HoldemHand.Phase.FINISHED;
        if (handRunning) {
            hand.forceFold(inHand);
            refund = inHand.takeStack();
            view.muck(index);
        }

        if (refund > 0) {
            plugin.chipBank().give(id, refund);
        }
        if (seat.chair != null) {
            seat.chair.release();
        }
        seats[index] = null;
        view.setBet(index, null);
        broadcast("<aqua>" + seat.name + "</aqua> <gray>left the table.</gray>");

        if (handRunning) {
            onHandChanged();
        }
        updateWaiting();
        render();
        return refund;
    }

    // ------------------------------------------------------------------ ticks

    /** Called once a second: stands up absent players, runs the countdown and the turn clock. */
    void tick() {
        emptySeconds = isEmpty() ? emptySeconds + 1 : 0;
        for (TableSeat seat : seats.clone()) {
            if (seat == null) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(seat.id);
            if (player == null || !player.isOnline()) {
                leave(seat.id);
                continue;
            }
            boolean near = player.getWorld().equals(station.world())
                    && player.getLocation().distanceSquared(layout.centre()) <= LEAVE_DISTANCE_SQUARED;
            if (!near) {
                long back = leave(seat.id);
                plugin.message(player, "<yellow>You walked away from the poker table. <white>"
                        + Text.chips(back) + "</white> chips were returned.</yellow>");
            }
        }

        switch (state) {
            case COUNTDOWN -> {
                if (readyCount() < config().pokerMinPlayers()) {
                    state = State.WAITING;
                } else if (--countdown <= 0) {
                    startHand();
                    return;
                }
                render();
            }
            case IN_HAND -> turnClock();
            default -> {
            }
        }
    }

    private void turnClock() {
        HandPlayer acting = hand.toAct();
        if (acting == null) {
            return;
        }
        turnSecondsLeft--;
        Player player = plugin.getServer().getPlayer(acting.id());
        if (player != null && turnSecondsLeft <= 10 && turnSecondsLeft > 0) {
            player.sendActionBar(Text.mm("<yellow>Your turn: <white>" + turnSecondsLeft
                    + "</white> seconds left</yellow>"));
        }
        if (turnSecondsLeft > 0) {
            return;
        }
        boolean check = hand.canCheck(acting);
        if (check) {
            hand.checkOrCall();
        } else {
            hand.fold();
            view.muck(acting.seat());
        }
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof PokerMenu) {
            player.closeInventory();
        }
        broadcast("<aqua>" + acting.name() + "</aqua> <gray>ran out of time and "
                + (check ? "checks" : "folds") + ".</gray>");
        onHandChanged();
    }

    private void updateWaiting() {
        int needed = config().pokerMinPlayers();
        if (state == State.WAITING && readyCount() >= needed) {
            state = State.COUNTDOWN;
            countdown = config().pokerStartDelaySeconds();
        } else if (state == State.COUNTDOWN && readyCount() < needed) {
            state = State.WAITING;
        }
    }

    // ------------------------------------------------------------------ hands

    private void startHand() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].stack > 0 && isOnline(seats[i].id)) {
                order.add(i);
            }
        }
        if (order.size() < Math.max(2, config().pokerMinPlayers())) {
            state = State.WAITING;
            render();
            return;
        }

        // The button moves to the next seat clockwise that is dealt in.
        int next = order.get(0);
        for (int seat : order) {
            if (seat > buttonSeat) {
                next = seat;
                break;
            }
        }
        buttonSeat = next;

        List<HandPlayer> players = new ArrayList<>(order.size());
        int buttonIndex = 0;
        for (int seat : order) {
            if (seat == buttonSeat) {
                buttonIndex = players.size();
            }
            TableSeat s = seats[seat];
            players.add(new HandPlayer(s.id, s.name, seat, s.stack));
        }

        CasinoConfig config = config();
        Deck deck = new Deck(1, 0.25);
        hand = new HoldemHand(players, buttonIndex, config.pokerSmallBlind(), config.pokerBigBlind(), deck::draw);
        hand.start();
        state = State.IN_HAND;
        lastAsked = null;
        result = "";

        for (HandPlayer p : hand.players()) {
            view.dealHole(p.seat(), p.hole(), plugin.getServer().getPlayer(p.id()));
        }
        view.moveButton(buttonSeat);
        broadcast("<gold>New hand.</gold> <aqua>" + seats[buttonSeat].name + "</aqua> <gray>has the button. "
                + "Blinds <white>" + Text.chips(config.pokerSmallBlind()) + "/"
                + Text.chips(config.pokerBigBlind()) + "</white>.</gray>");
        playAll(Sound.ITEM_BOOK_PAGE_TURN, 1.2f);
        onHandChanged();
    }

    /** Reacts to whatever the hand just did: ask the next player, deal the next street, or pay out. */
    private void onHandChanged() {
        if (hand == null) {
            return;
        }
        render();
        switch (hand.phase()) {
            case BETTING -> {
                HandPlayer acting = hand.toAct();
                if (acting != lastAsked) {
                    lastAsked = acting;
                    turn++;
                    turnSecondsLeft = config().pokerTurnSeconds();
                    promptTurn(acting);
                }
                refreshMenus();
            }
            case STREET_OVER -> {
                lastAsked = null;
                refreshMenus();
                if (pending == null) {
                    pending = Tasks.later(plugin, STREET_PAUSE_TICKS, () -> {
                        pending = null;
                        if (hand == null || hand.phase() != HoldemHand.Phase.STREET_OVER) {
                            return;
                        }
                        hand.advance();
                        view.showBoard(hand.board());
                        if (hand.phase() != HoldemHand.Phase.FINISHED) {
                            playAll(Sound.ITEM_BOOK_PAGE_TURN, 1.0f);
                        }
                        onHandChanged();
                    });
                }
            }
            case FINISHED -> {
                if (state == State.IN_HAND) {
                    finishHand();
                }
            }
        }
    }

    private void promptTurn(HandPlayer acting) {
        Player player = plugin.getServer().getPlayer(acting.id());
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        int askedTurn = turn;
        Tasks.later(plugin, MENU_DELAY_TICKS, () -> {
            if (askedTurn == turn && state == State.IN_HAND && player.isOnline()) {
                new PokerMenu(plugin, player, this).open();
            }
        });
    }

    /** A player's choice from the action menu. */
    void act(Player player, int forTurn, Move move, long amount) {
        if (hand == null || state != State.IN_HAND || forTurn != turn) {
            plugin.message(player, "<gray>That decision has already passed.</gray>");
            return;
        }
        HandPlayer p = hand.toAct();
        if (p == null || !p.id().equals(player.getUniqueId())) {
            plugin.message(player, "<red>It is not your turn.</red>");
            return;
        }

        String error;
        String said;
        switch (move) {
            case FOLD -> {
                error = hand.fold();
                said = "folds";
                if (error == null) {
                    view.muck(p.seat());
                }
            }
            case CHECK_OR_CALL -> {
                boolean check = hand.canCheck(p);
                long toCall = hand.toCall(p);
                error = hand.checkOrCall();
                said = check ? "checks"
                        : "calls <white>" + Text.chips(toCall) + "</white>" + (p.allIn() ? " and is all in" : "");
            }
            case RAISE -> {
                boolean opening = hand.currentBet() == 0;
                error = hand.raiseTo(amount);
                said = (opening ? "bets <white>" : "raises to <white>") + Text.chips(amount) + "</white>"
                        + (p.allIn() ? " and is all in" : "");
            }
            default -> {
                return;
            }
        }
        if (error != null) {
            plugin.message(player, "<red>" + error + "</red>");
            new PokerMenu(plugin, player, this).open();
            return;
        }
        broadcast("<aqua>" + p.name() + "</aqua> <gray>" + said + ".</gray>");
        playAll(move == Move.FOLD ? Sound.BLOCK_WOOL_PLACE : Sound.BLOCK_CHAIN_PLACE, 1.4f);
        onHandChanged();
    }

    private void finishHand() {
        state = State.BETWEEN_HANDS;
        lastAsked = null;

        if (hand.wentToShowdown()) {
            for (HandPlayer p : hand.players()) {
                if (!p.folded()) {
                    view.revealHole(p.seat());
                }
            }
        }
        view.showBoard(hand.board());

        List<String> lines = new ArrayList<>();
        for (HoldemHand.Award award : hand.awards()) {
            String with = award.hand() != null ? " with <white>" + award.hand().name() + "</white>" : "";
            broadcast("<aqua>" + award.player().name() + "</aqua> <green>wins <white>"
                    + Text.chips(award.amount()) + "</white> chips" + with + ".</green>");
            lines.add("<green>" + award.player().name() + " wins " + Text.chips(award.amount()) + "</green>"
                    + (award.hand() != null ? "\n<gray>" + award.hand().name() + "</gray>" : ""));
            Player winner = plugin.getServer().getPlayer(award.player().id());
            if (winner != null) {
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
        }
        result = String.join("\n", lines);

        // Everyone's stack goes back to their seat now the hand is settled.
        for (HandPlayer p : hand.players()) {
            TableSeat seat = seats[p.seat()];
            if (seat != null && seat.id.equals(p.id())) {
                seat.stack = p.stack();
            }
        }
        render();
        refreshMenus();

        Tasks.cancel(pending);
        pending = Tasks.later(plugin, hand.wentToShowdown() ? SHOWDOWN_TICKS : FOLDED_OUT_TICKS, this::endHand);
    }

    private void endHand() {
        pending = null;
        view.clearHand();
        hand = null;
        result = "";

        for (int i = 0; i < seats.length; i++) {
            TableSeat seat = seats[i];
            if (seat != null && seat.stack <= 0) {
                if (seat.chair != null) {
                    seat.chair.release();
                }
                seats[i] = null;
                Player player = plugin.getServer().getPlayer(seat.id);
                if (player != null) {
                    plugin.message(player, "<yellow>You are out of chips. Click the table to buy in again.</yellow>");
                }
            }
        }
        state = State.WAITING;
        updateWaiting();
        render();
    }

    // --------------------------------------------------------------- showing

    private void render() {
        view.setCentre(centreText());
        for (int i = 0; i < seats.length; i++) {
            view.setSeat(i, seatText(i));
            HandPlayer p = inHand(i);
            view.setBet(i, p != null && p.bet() > 0
                    ? "<yellow>" + Text.chips(p.bet()) + "</yellow>" : null);
        }
    }

    private String centreText() {
        CasinoConfig config = config();
        String title = "<aqua><bold>Texas Hold'em</bold></aqua>";
        String blinds = "<dark_gray>Blinds " + Text.chips(config.pokerSmallBlind()) + "/"
                + Text.chips(config.pokerBigBlind()) + "</dark_gray>";
        return switch (state) {
            case WAITING -> title + "\n<gray>Waiting for players <white>" + readyCount() + "/"
                    + config.pokerMinPlayers() + "</white></gray>\n" + blinds
                    + "\n<dark_gray>Right-click the table to sit</dark_gray>";
            case COUNTDOWN -> title + "\n<yellow>Next hand in <white>" + countdown + "</white></yellow>\n" + blinds;
            case IN_HAND -> {
                HandPlayer acting = hand.toAct();
                String doing = acting != null ? "<yellow>" + acting.name() + " to act</yellow>"
                        : "<gray>Dealing...</gray>";
                yield "<gold>Pot <white>" + Text.chips(hand.pot()) + "</white></gold>\n"
                        + "<gray>" + streetName(hand.street()) + "</gray>  " + doing;
            }
            case BETWEEN_HANDS -> result;
        };
    }

    private String seatText(int index) {
        TableSeat seat = seats[index];
        if (seat == null) {
            return "<dark_gray>Open seat</dark_gray>";
        }
        HandPlayer p = inHand(index);
        long stack = p != null ? p.stack() : seat.stack;
        boolean acting = p != null && hand.toAct() == p;

        StringBuilder name = new StringBuilder(acting ? "<yellow><bold>> " + seat.name + "</bold></yellow>"
                : "<aqua>" + seat.name + "</aqua>");
        if (p != null) {
            if (p == hand.smallBlindPlayer()) {
                name.append(" <gray>SB</gray>");
            } else if (p == hand.bigBlindPlayer()) {
                name.append(" <gray>BB</gray>");
            }
        }
        String status = "<white>" + Text.chips(stack) + "</white> <gray>chips</gray>";
        if (p != null && p.folded()) {
            status += "\n<dark_gray>Folded</dark_gray>";
        } else if (p != null && p.allIn()) {
            status += "\n<red>All in</red>";
        } else if (p == null && hand != null) {
            status += "\n<dark_gray>Next hand</dark_gray>";
        }
        if (p != null && !p.folded() && state == State.BETWEEN_HANDS && hand.wentToShowdown()) {
            List<Card> seven = new ArrayList<>(p.hole());
            seven.addAll(hand.board());
            status += "\n<gold>" + HandEvaluator.best(seven).name() + "</gold>";
        }
        return name + "\n" + status;
    }

    private static String streetName(HoldemHand.Street street) {
        return switch (street) {
            case PREFLOP -> "Pre-flop";
            case FLOP -> "Flop";
            case TURN -> "Turn";
            case RIVER -> "River";
            case SHOWDOWN -> "Showdown";
        };
    }

    /** Rebuilds any poker menu a player at this table has open, so it never shows stale chips. */
    private void refreshMenus() {
        for (TableSeat seat : seats) {
            if (seat == null) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(seat.id);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof PokerMenu menu
                    && menu.table() == this) {
                menu.refresh();
            }
        }
    }

    private void broadcast(String miniMessage) {
        for (TableSeat seat : seats) {
            if (seat != null) {
                Player player = plugin.getServer().getPlayer(seat.id);
                if (player != null) {
                    plugin.message(player, miniMessage);
                }
            }
        }
    }

    private void playAll(Sound sound, float pitch) {
        for (TableSeat seat : seats) {
            if (seat != null) {
                Player player = plugin.getServer().getPlayer(seat.id);
                if (player != null) {
                    player.playSound(layout.centre(), sound, 0.7f, pitch);
                }
            }
        }
    }

    // --------------------------------------------------------------- shutdown

    /**
     * Ends everything and returns every chip. A hand in progress is called off rather than
     * finished, so each player gets back exactly what they had before it started.
     */
    long shutdown() {
        closed = true;
        Tasks.cancel(pending);
        pending = null;
        long refunded = 0;

        boolean handRunning = hand != null && hand.phase() != HoldemHand.Phase.FINISHED;
        if (handRunning) {
            for (HandPlayer p : hand.players()) {
                long back = p.stack() + p.contributed();
                plugin.chipBank().give(p.id(), back);
                refunded += back;
            }
        }
        for (int i = 0; i < seats.length; i++) {
            TableSeat seat = seats[i];
            if (seat == null) {
                continue;
            }
            if (!handRunning || hand.player(seat.id) == null) {
                plugin.chipBank().give(seat.id, seat.stack);
                refunded += seat.stack;
            }
            if (seat.chair != null) {
                seat.chair.release();
            }
            seats[i] = null;
        }
        hand = null;
        view.remove();
        return refunded;
    }

    // ---------------------------------------------------------------- queries

    private HandPlayer inHand(int seatIndex) {
        TableSeat seat = seats[seatIndex];
        if (hand == null || seat == null) {
            return null;
        }
        HandPlayer p = hand.player(seat.id);
        return p != null && p.seat() == seatIndex ? p : null;
    }

    private int seatOf(UUID id) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int freeSeat() {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private int readyCount() {
        int count = 0;
        for (TableSeat seat : seats) {
            if (seat != null && seat.stack > 0 && isOnline(seat.id)) {
                count++;
            }
        }
        return count;
    }

    private boolean isOnline(UUID id) {
        Player player = plugin.getServer().getPlayer(id);
        return player != null && player.isOnline();
    }

    private CasinoConfig config() {
        return plugin.config();
    }

    boolean isSeated(UUID id) {
        return seatOf(id) >= 0;
    }

    /** True while the player still has cards in a hand that is being played. */
    boolean isInHand(UUID id) {
        if (hand == null || hand.phase() == HoldemHand.Phase.FINISHED) {
            return false;
        }
        HandPlayer p = hand.player(id);
        return p != null && !p.folded();
    }

    /** Empty for long enough that nobody is about to sit down. */
    boolean isAbandoned() {
        return isEmpty() && emptySeconds >= EMPTY_SECONDS_BEFORE_CLOSING;
    }

    boolean isEmpty() {
        for (TableSeat seat : seats) {
            if (seat != null) {
                return false;
            }
        }
        return true;
    }

    /** Chips the player has in front of them right now. */
    long stackOf(UUID id) {
        int index = seatOf(id);
        if (index < 0) {
            return 0;
        }
        HandPlayer p = inHand(index);
        return p != null ? p.stack() : seats[index].stack;
    }

    HoldemHand hand() {
        return state == State.IN_HAND || state == State.BETWEEN_HANDS ? hand : null;
    }

    State state() {
        return state;
    }

    int turn() {
        return turn;
    }

    int countdown() {
        return countdown;
    }

    Station station() {
        return station;
    }
}
