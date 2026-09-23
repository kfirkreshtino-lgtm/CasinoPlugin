package com.kfir.casino.game.poker;

import com.kfir.casino.game.card.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One player's part in a single hand: their stack, cards and chips in front of them.
 *
 * <p>The stack lives here for the length of the hand and is copied back to the table seat
 * when the hand ends, so the hand never has to reach out to the table mid-way.
 */
public final class HandPlayer {

    private final UUID id;
    private final String name;
    private final int seat;
    private final List<Card> hole = new ArrayList<>(2);

    long stack;
    /** Chips put in on the current street. */
    long bet;
    /** Chips put in over the whole hand, which is what the side pots are built from. */
    long contributed;
    boolean folded;
    boolean allIn;
    /** Has acted at least once on the current street. */
    boolean acted;
    /** How many full raises had happened when this player last acted. */
    int raisesSeen;

    public HandPlayer(UUID id, String name, int seat, long stack) {
        this.id = id;
        this.name = name;
        this.seat = seat;
        this.stack = stack;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Seat at the table, zero to five. */
    public int seat() {
        return seat;
    }

    public List<Card> hole() {
        return List.copyOf(hole);
    }

    void addHole(Card card) {
        hole.add(card);
    }

    public long stack() {
        return stack;
    }

    public long bet() {
        return bet;
    }

    public long contributed() {
        return contributed;
    }

    public boolean folded() {
        return folded;
    }

    public boolean allIn() {
        return allIn;
    }

    /** Moves chips from the stack into the pot, going all in if that empties the stack. */
    long put(long amount) {
        long paid = Math.min(amount, stack);
        stack -= paid;
        bet += paid;
        contributed += paid;
        if (stack == 0) {
            allIn = true;
        }
        return paid;
    }

    /** Takes the player's remaining stack away, used when they leave mid-hand. */
    long takeStack() {
        long left = stack;
        stack = 0;
        return left;
    }
}
