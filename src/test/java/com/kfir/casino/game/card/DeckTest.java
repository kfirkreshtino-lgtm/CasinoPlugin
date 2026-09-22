package com.kfir.casino.game.card;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckTest {

    @Test
    void shoeHasFiftyTwoCardsPerDeck() {
        Deck deck = new Deck(6, 0.25);
        assertEquals(312, deck.remaining());
        assertEquals(312, deck.totalCards());
    }

    @Test
    void singleDeckContainsEveryDistinctCard() {
        Deck deck = new Deck(1, 0.25);
        Set<Card> seen = new HashSet<>();
        while (deck.remaining() > 0) {
            seen.add(deck.draw());
        }
        assertEquals(52, seen.size());
    }

    @Test
    void drawingReducesTheShoe() {
        Deck deck = new Deck(1, 0.25);
        deck.draw();
        assertEquals(51, deck.remaining());
    }

    @Test
    void reshufflesOnlyPastThePenetrationPoint() {
        Deck deck = new Deck(1, 0.25);
        for (int i = 0; i < 10; i++) {
            deck.draw();
        }
        assertEquals(42, deck.remaining());
        assertFalse(deck.reshuffleIfNeeded());

        while (deck.remaining() > 13) {
            deck.draw();
        }
        assertTrue(deck.reshuffleIfNeeded());
        assertEquals(52, deck.remaining());
    }
}
