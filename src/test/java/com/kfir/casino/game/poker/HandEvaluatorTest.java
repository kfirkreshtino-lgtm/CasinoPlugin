package com.kfir.casino.game.poker;

import com.kfir.casino.game.card.Card;
import com.kfir.casino.game.card.Rank;
import com.kfir.casino.game.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    /** Parses cards like "AS KH 10D 2C". */
    static List<Card> cards(String text) {
        List<Card> cards = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            String rank = token.substring(0, token.length() - 1);
            char suit = token.charAt(token.length() - 1);
            Rank r = null;
            for (Rank candidate : Rank.values()) {
                if (candidate.symbol().equals(rank)) {
                    r = candidate;
                }
            }
            Suit s = switch (suit) {
                case 'S' -> Suit.SPADES;
                case 'H' -> Suit.HEARTS;
                case 'D' -> Suit.DIAMONDS;
                default -> Suit.CLUBS;
            };
            cards.add(new Card(r, s));
        }
        return cards;
    }

    private static HandValue best(String text) {
        return HandEvaluator.best(cards(text));
    }

    @Test
    void recognisesEveryCategory() {
        assertEquals(HandValue.STRAIGHT_FLUSH, best("9H 10H JH QH KH 2C 3D").category());
        assertEquals(HandValue.FOUR_OF_A_KIND, best("7S 7H 7D 7C KH 2C 3D").category());
        assertEquals(HandValue.FULL_HOUSE, best("7S 7H 7D KC KH 2C 3D").category());
        assertEquals(HandValue.FLUSH, best("2H 7H 9H JH KH 2C 3D").category());
        assertEquals(HandValue.STRAIGHT, best("5S 6H 7D 8C 9H 2C KD").category());
        assertEquals(HandValue.THREE_OF_A_KIND, best("7S 7H 7D 9C KH 2C 3D").category());
        assertEquals(HandValue.TWO_PAIR, best("7S 7H 9D 9C KH 2C 3D").category());
        assertEquals(HandValue.PAIR, best("7S 7H 9D JC KH 2C 3D").category());
        assertEquals(HandValue.HIGH_CARD, best("7S 4H 9D JC KH 2C 3D").category());
    }

    @Test
    void wheelIsAFiveHighStraight() {
        HandValue wheel = best("AS 2H 3D 4C 5H 9C KD");
        assertEquals(HandValue.STRAIGHT, wheel.category());
        assertTrue(best("2S 3H 4D 5C 6H 9C KD").score() > wheel.score());
    }

    @Test
    void royalFlushIsNamed() {
        assertEquals("Royal Flush", best("10S JS QS KS AS 2C 3D").name());
    }

    @Test
    void kickerDecidesBetweenEqualPairs() {
        HandValue aceKicker = best("KS KH AD 7C 4H 2C 3D");
        HandValue queenKicker = best("KD KC QD 7S 4S 2H 3C");
        assertTrue(aceKicker.score() > queenKicker.score());
    }

    @Test
    void boardPlaysForBothIsASplit() {
        String board = "AS KS QD JC 10H";
        HandValue one = HandEvaluator.best(cards(board + " 2C 3D"));
        HandValue two = HandEvaluator.best(cards(board + " 4H 5H"));
        assertEquals(one.score(), two.score());
    }

    @Test
    void fullHouseUsesBestTripsFromTwoSets() {
        HandValue hand = best("9S 9H 9D 4C 4H 4D 2C");
        assertEquals(HandValue.FULL_HOUSE, hand.category());
        assertEquals("Full House, Nines over Fours", hand.name());
    }

    @Test
    void describesPartialHands() {
        assertEquals("Pair of Aces", HandEvaluator.describe(cards("AS AH")));
        assertEquals("King high", HandEvaluator.describe(cards("KS 7H")));
    }
}
