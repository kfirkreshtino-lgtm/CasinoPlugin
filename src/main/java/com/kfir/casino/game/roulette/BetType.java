package com.kfir.casino.game.roulette;

/**
 * Supported roulette bets and their payout ratios.
 *
 * <p>The ratio is profit to stake, so a winning straight-up bet returns stake times
 * thirty-six in total. True adjacency bets (split, street, corner, line) are omitted
 * because they need a physical layout grid to select; every other standard bet is here.
 */
public enum BetType {

    STRAIGHT("Straight up", 35),
    RED("Red", 1),
    BLACK("Black", 1),
    ODD("Odd", 1),
    EVEN("Even", 1),
    LOW("1 to 18", 1),
    HIGH("19 to 36", 1),
    DOZEN_1("First dozen, 1 to 12", 2),
    DOZEN_2("Second dozen, 13 to 24", 2),
    DOZEN_3("Third dozen, 25 to 36", 2),
    COLUMN_1("First column", 2),
    COLUMN_2("Second column", 2),
    COLUMN_3("Third column", 2);

    private final String displayName;
    private final int ratio;

    BetType(String displayName, int ratio) {
        this.displayName = displayName;
        this.ratio = ratio;
    }

    public String displayName() {
        return displayName;
    }

    /** Profit per unit staked. Total returned on a win is stake times (ratio plus one). */
    public int ratio() {
        return ratio;
    }

    public boolean needsNumber() {
        return this == STRAIGHT;
    }
}
