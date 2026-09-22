package com.kfir.casino.game.card;

/** The four card suits. Symbols are unicode escapes so the source stays ASCII-safe. */
public enum Suit {

    HEARTS("♥", "red"),
    DIAMONDS("♦", "red"),
    CLUBS("♣", "dark_gray"),
    SPADES("♠", "dark_gray");

    private final String symbol;
    private final String color;

    Suit(String symbol, String color) {
        this.symbol = symbol;
        this.color = color;
    }

    public String symbol() {
        return symbol;
    }

    /** MiniMessage colour name used when rendering this suit. */
    public String color() {
        return color;
    }

    public boolean isRed() {
        return this == HEARTS || this == DIAMONDS;
    }
}
