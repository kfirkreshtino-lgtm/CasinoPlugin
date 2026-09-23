package com.kfir.casino.game.poker;

import com.kfir.casino.game.card.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the best five card hand out of a player's cards.
 *
 * <p>Texas Holdem gives each player seven cards at showdown, two in the hole and five on
 * the board. Rather than a clever lookup table this tries all twenty-one ways to pick five
 * of the seven and keeps the strongest, which is fast enough for a table of six and easy to
 * trust.
 */
public final class HandEvaluator {

    private static final String[] SINGULAR = {
            "", "", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Jack", "Queen", "King", "Ace"};
    private static final String[] PLURAL = {
            "", "", "Twos", "Threes", "Fours", "Fives", "Sixes", "Sevens", "Eights", "Nines", "Tens",
            "Jacks", "Queens", "Kings", "Aces"};

    private HandEvaluator() {
    }

    /** Best hand from five to seven cards. */
    public static HandValue best(List<Card> cards) {
        if (cards.size() < 5) {
            throw new IllegalArgumentException("Need at least five cards, got " + cards.size());
        }
        HandValue best = null;
        int n = cards.size();
        Card[] pick = new Card[5];
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            pick[0] = cards.get(a);
                            pick[1] = cards.get(b);
                            pick[2] = cards.get(c);
                            pick[3] = cards.get(d);
                            pick[4] = cards.get(e);
                            HandValue value = evaluate(pick);
                            if (best == null || value.score() > best.score()) {
                                best = value;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Short description of what a player holds so far, for the player's own menu. Works
     * from two cards up, before the board is complete.
     */
    public static String describe(List<Card> cards) {
        if (cards.size() >= 5) {
            return best(cards).name();
        }
        int[] counts = new int[15];
        int high = 0;
        for (Card card : cards) {
            int rank = card.rank().pokerValue();
            counts[rank]++;
            high = Math.max(high, rank);
        }
        int trips = 0;
        List<Integer> pairs = new ArrayList<>();
        for (int rank = 14; rank >= 2; rank--) {
            if (counts[rank] >= 3) {
                trips = rank;
            } else if (counts[rank] == 2) {
                pairs.add(rank);
            }
        }
        if (trips > 0) {
            return "Three " + PLURAL[trips];
        }
        if (pairs.size() >= 2) {
            return "Two Pair, " + PLURAL[pairs.get(0)] + " and " + PLURAL[pairs.get(1)];
        }
        if (pairs.size() == 1) {
            return "Pair of " + PLURAL[pairs.get(0)];
        }
        return SINGULAR[high] + " high";
    }

    /** Value of exactly five cards. */
    static HandValue evaluate(Card[] five) {
        int[] counts = new int[15];
        boolean flush = true;
        for (int i = 0; i < 5; i++) {
            counts[five[i].rank().pokerValue()]++;
            if (five[i].suit() != five[0].suit()) {
                flush = false;
            }
        }

        // Ranks ordered by how many of them there are, then by rank, which is exactly the
        // order poker compares them in: the quads before the kicker, the trips before the pair.
        int[] order = new int[5];
        int filled = 0;
        for (int count = 4; count >= 1; count--) {
            for (int rank = 14; rank >= 2; rank--) {
                if (counts[rank] == count) {
                    order[filled++] = rank;
                }
            }
        }
        int distinct = filled;
        int top = counts[order[0]];
        int second = distinct > 1 ? counts[order[1]] : 0;

        int straightHigh = 0;
        if (distinct == 5) {
            if (order[0] - order[4] == 4) {
                straightHigh = order[0];
            } else if (order[0] == 14 && order[1] == 5) {
                // Ace plays low in A-2-3-4-5, the wheel, which is a five-high straight.
                straightHigh = 5;
            }
        }

        if (straightHigh > 0 && flush) {
            String name = straightHigh == 14 ? "Royal Flush" : "Straight Flush, " + SINGULAR[straightHigh] + " high";
            return value(HandValue.STRAIGHT_FLUSH, name, straightHigh);
        }
        if (top == 4) {
            return value(HandValue.FOUR_OF_A_KIND, "Four " + PLURAL[order[0]], order[0], order[1]);
        }
        if (top == 3 && second == 2) {
            return value(HandValue.FULL_HOUSE, "Full House, " + PLURAL[order[0]] + " over " + PLURAL[order[1]],
                    order[0], order[1]);
        }
        if (flush) {
            return value(HandValue.FLUSH, "Flush, " + SINGULAR[order[0]] + " high",
                    order[0], order[1], order[2], order[3], order[4]);
        }
        if (straightHigh > 0) {
            return value(HandValue.STRAIGHT, "Straight, " + SINGULAR[straightHigh] + " high", straightHigh);
        }
        if (top == 3) {
            return value(HandValue.THREE_OF_A_KIND, "Three " + PLURAL[order[0]], order[0], order[1], order[2]);
        }
        if (top == 2 && second == 2) {
            return value(HandValue.TWO_PAIR, "Two Pair, " + PLURAL[order[0]] + " and " + PLURAL[order[1]],
                    order[0], order[1], order[2]);
        }
        if (top == 2) {
            return value(HandValue.PAIR, "Pair of " + PLURAL[order[0]], order[0], order[1], order[2], order[3]);
        }
        return value(HandValue.HIGH_CARD, SINGULAR[order[0]] + " high",
                order[0], order[1], order[2], order[3], order[4]);
    }

    /** Packs the category and the tie-break ranks into one number, most significant first. */
    private static HandValue value(int category, String name, int... ranks) {
        long score = category;
        for (int i = 0; i < 5; i++) {
            score = score * 15 + (i < ranks.length ? ranks[i] : 0);
        }
        return new HandValue(category, score, name);
    }
}
