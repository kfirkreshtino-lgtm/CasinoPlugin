package com.kfir.casino.game.poker;

import com.kfir.casino.game.card.Card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The rules of one hand of no-limit Texas Holdem, with nothing about Minecraft in it.
 *
 * <p>The table creates a hand with the players who are in it, in seat order, and tells it
 * where the dealer button is. The hand posts the blinds, deals, and then only moves when it
 * is told to: a player acts, or the table calls {@link #advance()} to deal the next street
 * once it has given everyone a moment to see the last bet. Keeping the pauses out of here
 * is what lets the rules be tested without a server.
 *
 * <p>Rules implemented: small and big blind, heads-up blind order, four betting rounds,
 * minimum raise equal to the last full raise, all-in for less, an all-in that is short of
 * a full raise does not reopen raising for players who already acted, side pots, split
 * pots with the odd chip going to the first winner left of the button, and uncontested
 * pots when everyone else folds.
 */
public final class HoldemHand {

    public enum Street {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    public enum Phase {
        /** Waiting for {@link #toAct()} to act. */
        BETTING,
        /** Betting on this street is over; the table should call {@link #advance()}. */
        STREET_OVER,
        /** Pots have been awarded. */
        FINISHED
    }

    /** Chips one player receives at the end of the hand. */
    public record Award(HandPlayer player, long amount, HandValue hand) {
    }

    /** A main or side pot and who can win it. */
    public record Pot(long amount, List<HandPlayer> eligible) {
    }

    private final List<HandPlayer> players;
    private final int button;
    private final int smallBlindIndex;
    private final int bigBlindIndex;
    private final long smallBlind;
    private final long bigBlind;
    private final Supplier<Card> deck;

    private final List<Card> board = new ArrayList<>(5);
    private Street street = Street.PREFLOP;
    private Phase phase = Phase.BETTING;
    private long currentBet;
    private long lastRaiseSize;
    private int fullRaises;
    private int toAct = -1;
    private boolean showdown;
    private List<Award> awards = List.of();

    /**
     * @param players    everyone dealt in, in seat order, each with chips
     * @param button     index into {@code players} of the dealer button
     * @param smallBlind small blind
     * @param bigBlind   big blind, also the minimum bet
     * @param deck       draws the next card from a shuffled deck
     */
    public HoldemHand(List<HandPlayer> players, int button, long smallBlind, long bigBlind, Supplier<Card> deck) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("A hand needs at least two players");
        }
        this.players = List.copyOf(players);
        this.button = button;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.deck = deck;
        if (this.players.size() == 2) {
            // Heads up the button posts the small blind and acts first before the flop.
            this.smallBlindIndex = button;
            this.bigBlindIndex = next(button);
        } else {
            this.smallBlindIndex = next(button);
            this.bigBlindIndex = next(smallBlindIndex);
        }
    }

    /** Posts the blinds, deals two cards each and finds the first player to act. */
    public void start() {
        players.get(smallBlindIndex).put(smallBlind);
        players.get(bigBlindIndex).put(bigBlind);
        currentBet = bigBlind;
        lastRaiseSize = bigBlind;

        for (int round = 0; round < 2; round++) {
            for (int k = 1; k <= players.size(); k++) {
                players.get((button + k) % players.size()).addHole(deck.get());
            }
        }

        int first = players.size() == 2 ? smallBlindIndex : next(bigBlindIndex);
        beginBetting(first);
    }

    // ------------------------------------------------------------------ actions

    /** Folds for the player to act. */
    public String fold() {
        HandPlayer p = acting();
        if (p == null) {
            return "It is not anyone's turn.";
        }
        p.folded = true;
        finishAction(p);
        return null;
    }

    /** Checks if there is nothing to call, otherwise calls, all in if that is all there is. */
    public String checkOrCall() {
        HandPlayer p = acting();
        if (p == null) {
            return "It is not anyone's turn.";
        }
        p.put(Math.max(0, currentBet - p.bet));
        finishAction(p);
        return null;
    }

    /**
     * Bets or raises so the player's total on this street becomes {@code total}.
     * Anything below a full raise is only allowed as an all in.
     */
    public String raiseTo(long total) {
        HandPlayer p = acting();
        if (p == null) {
            return "It is not anyone's turn.";
        }
        if (!canRaise(p)) {
            return "You cannot raise right now.";
        }
        long max = maxRaiseTo(p);
        if (total > max) {
            return "You only have " + p.stack + " chips.";
        }
        if (total < minRaiseTo(p) && total != max) {
            return "The minimum raise is to " + minRaiseTo(p) + ".";
        }
        long raiseSize = total - currentBet;
        p.put(total - p.bet);
        if (raiseSize >= lastRaiseSize) {
            lastRaiseSize = raiseSize;
            fullRaises++;
        }
        currentBet = total;
        finishAction(p);
        return null;
    }

    /**
     * Folds a player out of turn, used when they leave the table mid-hand. Chips already in
     * the pot stay there.
     */
    public void forceFold(HandPlayer p) {
        if (phase == Phase.FINISHED || p.folded) {
            return;
        }
        boolean wasActing = toAct >= 0 && players.get(toAct) == p;
        p.folded = true;
        if (activeCount() == 1) {
            finishUncontested();
        } else if (wasActing) {
            finishAction(p);
        } else if (phase == Phase.BETTING) {
            // The player to act may now have nobody left to bet against.
            int next = findNextToAct(toAct);
            toAct = next;
            if (next < 0) {
                phase = Phase.STREET_OVER;
            }
        }
    }

    private void finishAction(HandPlayer p) {
        p.acted = true;
        p.raisesSeen = fullRaises;
        if (activeCount() == 1) {
            finishUncontested();
            return;
        }
        int next = findNextToAct(next(players.indexOf(p)));
        toAct = next;
        if (next < 0) {
            phase = Phase.STREET_OVER;
        }
    }

    // ------------------------------------------------------------------ streets

    /** Deals the next street, or goes to showdown after the river. */
    public void advance() {
        if (phase != Phase.STREET_OVER) {
            throw new IllegalStateException("Betting is still open");
        }
        for (HandPlayer p : players) {
            p.bet = 0;
            p.acted = false;
        }
        currentBet = 0;
        lastRaiseSize = bigBlind;

        switch (street) {
            case PREFLOP -> {
                deck.get();
                board.add(deck.get());
                board.add(deck.get());
                board.add(deck.get());
                street = Street.FLOP;
            }
            case FLOP -> {
                deck.get();
                board.add(deck.get());
                street = Street.TURN;
            }
            case TURN -> {
                deck.get();
                board.add(deck.get());
                street = Street.RIVER;
            }
            case RIVER, SHOWDOWN -> {
                showdown();
                return;
            }
        }
        beginBetting(next(button));
    }

    private void beginBetting(int from) {
        toAct = findNextToAct(from);
        phase = toAct < 0 ? Phase.STREET_OVER : Phase.BETTING;
    }

    /**
     * Next player, starting at {@code from}, who still owes a decision. A player owes one
     * if they have not acted on this street or someone has bet more since. With nobody left
     * to bet against, a player who has already matched the bet is skipped, which is how an
     * all-in hand runs out the board without asking anyone.
     */
    private int findNextToAct(int from) {
        if (from < 0) {
            return -1;
        }
        int canAct = 0;
        for (HandPlayer p : players) {
            if (!p.folded && !p.allIn) {
                canAct++;
            }
        }
        for (int k = 0; k < players.size(); k++) {
            int i = (from + k) % players.size();
            HandPlayer p = players.get(i);
            if (p.folded || p.allIn) {
                continue;
            }
            boolean owes = !p.acted || p.bet < currentBet;
            if (!owes) {
                continue;
            }
            if (canAct <= 1 && p.bet >= currentBet) {
                continue;
            }
            return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- settling

    private void finishUncontested() {
        HandPlayer winner = null;
        long total = 0;
        for (HandPlayer p : players) {
            total += p.contributed;
            if (!p.folded) {
                winner = p;
            }
        }
        winner.stack += total;
        awards = List.of(new Award(winner, total, null));
        toAct = -1;
        phase = Phase.FINISHED;
    }

    private void showdown() {
        street = Street.SHOWDOWN;
        showdown = true;
        Map<HandPlayer, HandValue> values = new LinkedHashMap<>();
        for (HandPlayer p : players) {
            if (!p.folded) {
                List<Card> seven = new ArrayList<>(p.hole());
                seven.addAll(board);
                values.put(p, HandEvaluator.best(seven));
            }
        }

        Map<HandPlayer, Long> won = new LinkedHashMap<>();
        for (Pot pot : pots(players)) {
            long best = Long.MIN_VALUE;
            for (HandPlayer p : pot.eligible()) {
                best = Math.max(best, values.get(p).score());
            }
            // Winners in order from the first seat left of the button, who gets any odd chip.
            List<HandPlayer> winners = new ArrayList<>();
            for (int k = 1; k <= players.size(); k++) {
                HandPlayer p = players.get((button + k) % players.size());
                if (pot.eligible().contains(p) && values.get(p).score() == best) {
                    winners.add(p);
                }
            }
            long share = pot.amount() / winners.size();
            long odd = pot.amount() % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                long amount = share + (i < odd ? 1 : 0);
                won.merge(winners.get(i), amount, Long::sum);
            }
        }

        List<Award> result = new ArrayList<>();
        for (Map.Entry<HandPlayer, Long> entry : won.entrySet()) {
            entry.getKey().stack += entry.getValue();
            result.add(new Award(entry.getKey(), entry.getValue(), values.get(entry.getKey())));
        }
        awards = List.copyOf(result);
        toAct = -1;
        phase = Phase.FINISHED;
    }

    /**
     * Splits everything put in this hand into a main pot and side pots.
     *
     * <p>Each distinct amount a player still in the hand has put in marks a layer. A layer
     * collects up to that amount from everyone, folded players included, and can be won by
     * anyone still in who put in at least that much. Chips a folded player put in above
     * every remaining player go to the top pot.
     */
    static List<Pot> pots(List<HandPlayer> players) {
        List<Long> levels = new ArrayList<>();
        for (HandPlayer p : players) {
            if (!p.folded && p.contributed > 0 && !levels.contains(p.contributed)) {
                levels.add(p.contributed);
            }
        }
        levels.sort(Long::compare);

        List<Pot> pots = new ArrayList<>();
        long previous = 0;
        for (long level : levels) {
            long amount = 0;
            List<HandPlayer> eligible = new ArrayList<>();
            for (HandPlayer p : players) {
                amount += Math.min(p.contributed, level) - Math.min(p.contributed, previous);
                if (!p.folded && p.contributed >= level) {
                    eligible.add(p);
                }
            }
            if (amount > 0) {
                pots.add(new Pot(amount, List.copyOf(eligible)));
            }
            previous = level;
        }

        long leftover = 0;
        for (HandPlayer p : players) {
            leftover += Math.max(0, p.contributed - previous);
        }
        if (leftover > 0 && !pots.isEmpty()) {
            Pot top = pots.remove(pots.size() - 1);
            pots.add(new Pot(top.amount() + leftover, top.eligible()));
        }
        return pots;
    }

    // ----------------------------------------------------------------- queries

    private HandPlayer acting() {
        if (phase != Phase.BETTING || toAct < 0) {
            return null;
        }
        return players.get(toAct);
    }

    private int next(int index) {
        return (index + 1) % players.size();
    }

    private int activeCount() {
        int count = 0;
        for (HandPlayer p : players) {
            if (!p.folded) {
                count++;
            }
        }
        return count;
    }

    /** The player whose decision it is, or null. */
    public HandPlayer toAct() {
        return acting();
    }

    public long toCall(HandPlayer p) {
        return Math.min(Math.max(0, currentBet - p.bet), p.stack);
    }

    public boolean canCheck(HandPlayer p) {
        return p.bet >= currentBet;
    }

    /**
     * Whether the player may bet or raise. They need chips beyond calling, someone left to
     * raise against, and either not to have acted yet or to be facing a full raise since.
     */
    public boolean canRaise(HandPlayer p) {
        if (p.folded || p.allIn || p.stack <= currentBet - p.bet) {
            return false;
        }
        boolean someoneElse = false;
        for (HandPlayer other : players) {
            if (other != p && !other.folded && !other.allIn) {
                someoneElse = true;
                break;
            }
        }
        return someoneElse && (!p.acted || p.raisesSeen < fullRaises);
    }

    /** Smallest total a raise can make, capped at the player's all in. */
    public long minRaiseTo(HandPlayer p) {
        return Math.min(currentBet + lastRaiseSize, maxRaiseTo(p));
    }

    public long maxRaiseTo(HandPlayer p) {
        return p.bet + p.stack;
    }

    /** Everything in the middle, including bets on the current street. */
    public long pot() {
        long total = 0;
        for (HandPlayer p : players) {
            total += p.contributed;
        }
        return total;
    }

    public long currentBet() {
        return currentBet;
    }

    public List<HandPlayer> players() {
        return players;
    }

    public HandPlayer player(java.util.UUID id) {
        for (HandPlayer p : players) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public HandPlayer buttonPlayer() {
        return players.get(button);
    }

    public HandPlayer smallBlindPlayer() {
        return players.get(smallBlindIndex);
    }

    public HandPlayer bigBlindPlayer() {
        return players.get(bigBlindIndex);
    }

    public List<Card> board() {
        return List.copyOf(board);
    }

    public Street street() {
        return street;
    }

    public Phase phase() {
        return phase;
    }

    /** True when the hand ended by comparing cards rather than by everyone else folding. */
    public boolean wentToShowdown() {
        return showdown;
    }

    public List<Award> awards() {
        return awards;
    }

    public long bigBlind() {
        return bigBlind;
    }
}
