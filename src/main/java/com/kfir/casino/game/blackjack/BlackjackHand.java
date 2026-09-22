package com.kfir.casino.game.blackjack;

import com.kfir.casino.game.card.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A blackjack hand with soft-ace handling.
 *
 * <p>Aces start at eleven and are demoted to one, one at a time, until the total is
 * twenty-one or below. A hand is soft while an ace is still counted as eleven.
 */
public final class BlackjackHand {

    private final List<Card> cards = new ArrayList<>();

    public void add(Card card) {
        cards.add(card);
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    public void clear() {
        cards.clear();
    }

    /** Best total that does not bust, or the minimum total when every option busts. */
    public int total() {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            total += card.rank().blackjackValue();
            if (card.rank().isAce()) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** True while an ace is still being counted as eleven. */
    public boolean isSoft() {
        int hard = 0;
        int aces = 0;
        for (Card card : cards) {
            if (card.rank().isAce()) {
                aces++;
                hard += 1;
            } else {
                hard += card.rank().blackjackValue();
            }
        }
        return aces > 0 && hard + 10 <= 21;
    }

    public boolean isBust() {
        return total() > 21;
    }

    /** A natural: exactly two cards making twenty-one. */
    public boolean isNatural() {
        return cards.size() == 2 && total() == 21;
    }

    /** Rendered as a coloured MiniMessage string for chat. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        for (Card card : cards) {
            if (builder.length() > 0) {
                builder.append("<gray>, </gray>");
            }
            builder.append(card.colored());
        }
        return builder.toString();
    }
}
