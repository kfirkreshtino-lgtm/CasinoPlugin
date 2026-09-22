package com.kfir.casino.game.blackjack;

import com.kfir.casino.game.card.Card;
import com.kfir.casino.game.card.Rank;
import com.kfir.casino.game.card.Suit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackHandTest {

    private static BlackjackHand handOf(Rank... ranks) {
        BlackjackHand hand = new BlackjackHand();
        for (Rank rank : ranks) {
            hand.add(new Card(rank, Suit.SPADES));
        }
        return hand;
    }

    @Test
    void countsNumberCards() {
        assertEquals(15, handOf(Rank.SEVEN, Rank.EIGHT).total());
    }

    @Test
    void countsAceAsElevenWhenItFits() {
        BlackjackHand hand = handOf(Rank.ACE, Rank.SIX);
        assertEquals(17, hand.total());
        assertTrue(hand.isSoft());
    }

    @Test
    void demotesAceToAvoidBust() {
        BlackjackHand hand = handOf(Rank.ACE, Rank.SIX, Rank.KING);
        assertEquals(17, hand.total());
        assertFalse(hand.isSoft());
    }

    @Test
    void demotesMultipleAces() {
        assertEquals(13, handOf(Rank.ACE, Rank.ACE, Rank.ACE).total());
        assertEquals(12, handOf(Rank.ACE, Rank.ACE).total());
    }

    @Test
    void detectsNaturalOnlyOnTwoCards() {
        assertTrue(handOf(Rank.ACE, Rank.KING).isNatural());
        assertFalse(handOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN).isNatural());
        assertEquals(21, handOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN).total());
    }

    @Test
    void detectsBust() {
        BlackjackHand hand = handOf(Rank.KING, Rank.QUEEN, Rank.TWO);
        assertTrue(hand.isBust());
        assertEquals(22, hand.total());
    }

    @Test
    void softSeventeenIsSoft() {
        BlackjackHand hand = handOf(Rank.ACE, Rank.THREE, Rank.THREE);
        assertEquals(17, hand.total());
        assertTrue(hand.isSoft());
    }
}
