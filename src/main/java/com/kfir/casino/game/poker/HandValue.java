package com.kfir.casino.game.poker;

/**
 * Strength of a five card poker hand.
 *
 * @param category 0 high card up to 8 straight flush
 * @param score    total order over hands: a higher score always beats a lower one, and
 *                 equal scores split the pot
 * @param name     how the hand is announced, for example "Two Pair, Kings and Fives"
 */
public record HandValue(int category, long score, String name) implements Comparable<HandValue> {

    public static final int HIGH_CARD = 0;
    public static final int PAIR = 1;
    public static final int TWO_PAIR = 2;
    public static final int THREE_OF_A_KIND = 3;
    public static final int STRAIGHT = 4;
    public static final int FLUSH = 5;
    public static final int FULL_HOUSE = 6;
    public static final int FOUR_OF_A_KIND = 7;
    public static final int STRAIGHT_FLUSH = 8;

    @Override
    public int compareTo(HandValue other) {
        return Long.compare(score, other.score);
    }
}
