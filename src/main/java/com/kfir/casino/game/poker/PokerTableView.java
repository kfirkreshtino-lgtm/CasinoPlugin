package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.card.Card;
import com.kfir.casino.table.CardVisual;
import com.kfir.casino.table.ChipStack;
import com.kfir.casino.table.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a poker table shows in the world: hole cards, the board, bets, the dealer
 * button and the floating labels.
 *
 * <p>Cards look exactly like the blackjack cards. The difference is the hole cards, which
 * are private: see {@link HoleCard}. Chips are real piles on the felt: what each player has
 * in front of them, what they have bet on this street, and the pot.
 */
final class PokerTableView {

    private final CasinoPlugin plugin;
    private final PokerLayout layout;

    private final Hologram centre;
    private final Hologram[] seatLabels = new Hologram[PokerLayout.SEATS];
    private final Hologram[] bets = new Hologram[PokerLayout.SEATS];
    private final ChipStack[] playerChips = new ChipStack[PokerLayout.SEATS];
    private final ChipStack[] betChips = new ChipStack[PokerLayout.SEATS];
    private final ChipStack pot;
    private Hologram dealerButton;

    private final Map<Integer, List<HoleCard>> hole = new HashMap<>();
    private final List<CardVisual> board = new ArrayList<>();

    PokerTableView(CasinoPlugin plugin, PokerLayout layout) {
        this.plugin = plugin;
        this.layout = layout;
        this.centre = Hologram.spawn(layout.centreLabel(), "", 0.8f);
        for (int seat = 0; seat < PokerLayout.SEATS; seat++) {
            seatLabels[seat] = Hologram.spawn(layout.seatLabel(seat), "", 0.55f);
            playerChips[seat] = new ChipStack(layout.playerChips(seat));
            betChips[seat] = new ChipStack(layout.bet(seat));
        }
        this.pot = new ChipStack(layout.pot());
    }

    /** Piles up a player's chips and their bet on this street. */
    void setChips(int seat, long behind, long bet) {
        playerChips[seat].set(behind);
        betChips[seat].set(bet);
    }

    void setPot(long amount) {
        pot.set(amount);
    }

    void setCentre(String text) {
        centre.setText(text);
    }

    void setSeat(int seat, String text) {
        seatLabels[seat].setText(text);
    }

    /** Shows a player's bet in front of them, or clears it with null. */
    void setBet(int seat, String text) {
        if (text == null) {
            if (bets[seat] != null) {
                bets[seat].remove();
                bets[seat] = null;
            }
            return;
        }
        if (bets[seat] == null) {
            bets[seat] = Hologram.spawn(layout.betLabel(seat), text, 0.5f);
        } else {
            bets[seat].setText(text);
        }
    }

    void moveButton(int seat) {
        Location where = layout.dealerButton(seat);
        if (dealerButton == null) {
            dealerButton = Hologram.spawn(where, "<white><bold>(D)</bold></white>", 0.5f);
        } else {
            dealerButton.moveTo(where);
        }
    }

    /** Deals a player's two cards: face up for them, face down for everyone else. */
    void dealHole(int seat, List<Card> cards, Player owner) {
        muck(seat);
        List<HoleCard> dealt = new ArrayList<>(cards.size());
        for (int i = 0; i < cards.size(); i++) {
            dealt.add(new HoleCard(layout.holeCard(seat, i), cards.get(i), owner));
        }
        hole.put(seat, dealt);
    }

    /** Turns a player's cards face up for the whole table, at showdown. */
    void revealHole(int seat) {
        List<HoleCard> cards = hole.get(seat);
        if (cards != null) {
            cards.forEach(HoleCard::revealToAll);
        }
    }

    /** Takes a player's cards off the table without showing them, when they fold. */
    void muck(int seat) {
        List<HoleCard> cards = hole.remove(seat);
        if (cards != null) {
            cards.forEach(HoleCard::remove);
        }
    }

    /** Puts any newly dealt community cards on the felt. */
    void showBoard(List<Card> cards) {
        for (int i = board.size(); i < cards.size(); i++) {
            board.add(CardVisual.place(layout.boardCard(i), cards.get(i)));
        }
    }

    /** Clears the cards, bets and button between hands. The labels stay. */
    void clearHand() {
        for (int seat : new ArrayList<>(hole.keySet())) {
            muck(seat);
        }
        board.forEach(CardVisual::remove);
        board.clear();
        for (int seat = 0; seat < PokerLayout.SEATS; seat++) {
            setBet(seat, null);
            betChips[seat].set(0);
        }
        pot.set(0);
        if (dealerButton != null) {
            dealerButton.remove();
            dealerButton = null;
        }
    }

    void remove() {
        clearHand();
        centre.remove();
        for (Hologram label : seatLabels) {
            label.remove();
        }
        for (int seat = 0; seat < PokerLayout.SEATS; seat++) {
            playerChips[seat].remove();
            betChips[seat].remove();
        }
        pot.remove();
    }

    /**
     * A hole card, which is really two cards at the same spot. The owner is sent only the
     * face up one and everyone else only the face down one, so the face never reaches
     * another player's game at all.
     */
    private final class HoleCard {

        private final Location spot;
        private final Card card;
        private CardVisual back;
        private CardVisual face;
        private CardVisual revealed;

        HoleCard(Location spot, Card card, Player owner) {
            this.spot = spot;
            this.card = card;
            this.back = CardVisual.place(spot, null);
            if (owner != null && owner.isOnline()) {
                back.hideFrom(plugin, owner);
                this.face = CardVisual.placeFor(plugin, spot, card, owner);
            }
        }

        void revealToAll() {
            if (revealed != null) {
                return;
            }
            removePrivate();
            revealed = CardVisual.place(spot, card);
        }

        private void removePrivate() {
            if (back != null) {
                back.remove();
                back = null;
            }
            if (face != null) {
                face.remove();
                face = null;
            }
        }

        void remove() {
            removePrivate();
            if (revealed != null) {
                revealed.remove();
                revealed = null;
            }
        }
    }
}
