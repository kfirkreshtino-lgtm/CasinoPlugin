package com.kfir.casino.game.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A multi-deck shoe. Reshuffling happens between hands, never mid-hand, so a hand
 * is always dealt from a single consistent shoe.
 */
public final class Deck {

    private final int deckCount;
    private final double reshuffleAt;
    private final List<Card> cards = new ArrayList<>();

    public Deck(int deckCount, double reshuffleAt) {
        this.deckCount = Math.max(1, deckCount);
        this.reshuffleAt = Math.min(Math.max(reshuffleAt, 0.05), 0.9);
        reshuffle();
    }

    /** Rebuilds and shuffles the full shoe. */
    public void reshuffle() {
        cards.clear();
        for (int d = 0; d < deckCount; d++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(rank, suit));
                }
            }
        }
        Collections.shuffle(cards, ThreadLocalRandom.current());
    }

    /** Call at the start of a hand. Reshuffles only once the shoe is past its penetration point. */
    public boolean reshuffleIfNeeded() {
        if (cards.size() <= totalCards() * reshuffleAt) {
            reshuffle();
            return true;
        }
        return false;
    }

    public Card draw() {
        if (cards.isEmpty()) {
            reshuffle();
        }
        return cards.remove(cards.size() - 1);
    }

    public int remaining() {
        return cards.size();
    }

    public int totalCards() {
        return deckCount * 52;
    }
}
