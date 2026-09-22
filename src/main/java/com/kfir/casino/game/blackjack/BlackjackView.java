package com.kfir.casino.game.blackjack;

/**
 * How a blackjack game presents itself.
 *
 * <p>The game holds no rendering code at all. It announces what happened and the view
 * decides how to show it, which is what allowed the chest menu to be replaced with a
 * physical table without touching a single rule.
 */
public interface BlackjackView {

    /** Redraw totals, the current bet and the status line. */
    void render();

    /** A card was just added to a hand. Animate it onto the table. */
    void dealt(boolean dealer);

    /** The dealer turns their hole card face up. */
    void revealHole();

    /** The hand is over. Show the outcome. */
    void finished();

    /** Clear the table ready for the next hand. */
    void reset();

    /** Run once every queued animation has played out. */
    void whenSettled(Runnable action);

    /** Remove every entity this view owns. */
    void close();
}
