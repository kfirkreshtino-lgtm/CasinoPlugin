package com.kfir.casino.game.card;

/** An immutable playing card. */
public record Card(Rank rank, Suit suit) {

    /** Short human label such as {@code A}+heart. */
    public String label() {
        return rank.symbol() + suit.symbol();
    }

    /** MiniMessage-coloured label for chat and item names. */
    public String colored() {
        return "<" + suit.color() + ">" + label() + "</" + suit.color() + ">";
    }

    @Override
    public String toString() {
        return label();
    }
}
