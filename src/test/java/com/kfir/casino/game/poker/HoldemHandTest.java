package com.kfir.casino.game.poker;

import com.kfir.casino.game.card.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldemHandTest {

    private static List<HandPlayer> players(long... stacks) {
        List<HandPlayer> list = new ArrayList<>();
        for (int i = 0; i < stacks.length; i++) {
            list.add(new HandPlayer(UUID.randomUUID(), "P" + i, i, stacks[i]));
        }
        return list;
    }

    /** A deck that deals exactly the given cards, in order. */
    private static java.util.function.Supplier<Card> deck(String cards) {
        Deque<Card> queue = new ArrayDeque<>(HandEvaluatorTest.cards(cards));
        return queue::removeFirst;
    }

    /** A deck of low junk cards, for tests where the cards do not matter. */
    private static java.util.function.Supplier<Card> anyDeck() {
        return deck("2C 3D 4H 5S 7C 8D 9H JS 2D 3H 4S 5C 7D 8H 9S JC 2H 3S 4C 5D 7H 8S 9C");
    }

    private static long total(List<HandPlayer> players) {
        long sum = 0;
        for (HandPlayer p : players) {
            sum += p.stack();
        }
        return sum;
    }

    @Test
    void headsUpButtonPostsSmallBlindAndActsFirst() {
        List<HandPlayer> ps = players(100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();

        assertSame(ps.get(0), hand.smallBlindPlayer());
        assertEquals(5, ps.get(0).bet());
        assertEquals(10, ps.get(1).bet());
        assertSame(ps.get(0), hand.toAct());
        assertEquals(2, ps.get(0).hole().size());
    }

    @Test
    void bigBlindGetsTheOptionAfterACall() {
        List<HandPlayer> ps = players(100, 100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();

        // Button is 0, so 1 posts small, 2 posts big and 0 acts first.
        assertSame(ps.get(0), hand.toAct());
        hand.checkOrCall();
        hand.checkOrCall();
        assertSame(ps.get(2), hand.toAct());
        assertTrue(hand.canCheck(ps.get(2)));
        assertTrue(hand.canRaise(ps.get(2)));
        hand.checkOrCall();
        assertEquals(HoldemHand.Phase.STREET_OVER, hand.phase());
    }

    @Test
    void postflopActionStartsLeftOfTheButton() {
        List<HandPlayer> ps = players(100, 100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();
        hand.checkOrCall();
        hand.checkOrCall();
        hand.checkOrCall();
        hand.advance();

        assertEquals(HoldemHand.Street.FLOP, hand.street());
        assertEquals(3, hand.board().size());
        assertSame(ps.get(1), hand.toAct());
    }

    @Test
    void everyoneFoldingGivesThePotWithoutShowdown() {
        List<HandPlayer> ps = players(100, 100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();
        hand.fold();
        hand.fold();

        assertEquals(HoldemHand.Phase.FINISHED, hand.phase());
        assertFalse(hand.wentToShowdown());
        assertEquals(105, ps.get(2).stack());
        assertEquals(300, total(ps));
    }

    @Test
    void raiseBelowTheMinimumIsRefused() {
        List<HandPlayer> ps = players(100, 100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();

        assertEquals(20, hand.minRaiseTo(ps.get(0)));
        assertNotNull(hand.raiseTo(15));
        assertNull(hand.raiseTo(30));
        // The raise was 20, so the next raise must be to at least 50.
        assertEquals(50, hand.minRaiseTo(hand.toAct()));
    }

    @Test
    void shortAllInDoesNotReopenRaising() {
        // Player 2 has only 25 chips, so going all in over a raise to 20 is not a full raise.
        List<HandPlayer> ps = players(200, 200, 200, 25);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();

        assertSame(ps.get(3), hand.toAct());
        hand.checkOrCall();                 // 3 calls 10
        hand.raiseTo(20);                   // 0 raises to 20
        hand.checkOrCall();                 // 1 calls
        hand.checkOrCall();                 // 2 calls
        assertSame(ps.get(3), hand.toAct());
        assertNull(hand.raiseTo(25));       // 3 all in for 25, short of a full raise

        assertSame(ps.get(0), hand.toAct());
        assertEquals(5, hand.toCall(ps.get(0)));
        assertFalse(hand.canRaise(ps.get(0)));
    }

    @Test
    void sidePotsAreSplitByWhoCanWinThem() {
        // P0 is all in for 50 with the best hand, P1 and P2 have 200 each.
        // Deal order starts left of the button (P2): P0, P1, P2, then again.
        List<HandPlayer> ps = players(50, 200, 200);
        String cards = "AS KS QS AD KD QD"      // P0: A A, P1: K K, P2: Q Q
                + " 2C 7H 8D 9C 3S 4H 5D 6C 2S"; // burn, flop, burn, turn, burn, river
        HoldemHand hand = new HoldemHand(ps, 2, 5, 10, deck(cards));
        hand.start();

        // Button is P2, so P0 posts small, P1 big, P2 acts first.
        hand.raiseTo(100);  // P2
        hand.checkOrCall(); // P0 all in for 50
        hand.checkOrCall(); // P1 calls 100
        assertEquals(250, hand.pot());
        while (hand.phase() != HoldemHand.Phase.FINISHED) {
            if (hand.phase() == HoldemHand.Phase.STREET_OVER) {
                hand.advance();
            } else {
                hand.checkOrCall();
            }
        }

        assertTrue(hand.wentToShowdown());
        // Main pot 150 to P0's aces; side pot 100 between P1 and P2 goes to P1's kings.
        assertEquals(150, ps.get(0).stack());
        assertEquals(200, ps.get(1).stack());
        assertEquals(100, ps.get(2).stack());
        assertEquals(450, total(ps));
    }

    @Test
    void foldedPlayersChipsStayInThePot() {
        List<HandPlayer> players = players(100, 100, 100);
        List<HoldemHand.Pot> pots;
        players.get(0).put(35);
        players.get(1).put(35);
        players.get(2).put(35);
        players.get(2).folded = true;
        pots = HoldemHand.pots(players);
        assertEquals(1, pots.size());
        assertEquals(105, pots.get(0).amount());
        assertEquals(2, pots.get(0).eligible().size());
    }

    @Test
    void allInPlayersRunOutTheBoardWithoutActing() {
        List<HandPlayer> ps = players(100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();
        hand.raiseTo(100);
        hand.checkOrCall();

        int streets = 0;
        while (hand.phase() == HoldemHand.Phase.STREET_OVER) {
            assertNull(hand.toAct());
            hand.advance();
            streets++;
        }
        assertEquals(4, streets);
        assertEquals(HoldemHand.Phase.FINISHED, hand.phase());
        assertEquals(200, total(ps));
    }

    @Test
    void leavingOutOfTurnKeepsTheHandGoing() {
        List<HandPlayer> ps = players(100, 100, 100);
        HoldemHand hand = new HoldemHand(ps, 0, 5, 10, anyDeck());
        hand.start();

        hand.forceFold(ps.get(1));
        assertSame(ps.get(0), hand.toAct());
        hand.fold();
        assertEquals(HoldemHand.Phase.FINISHED, hand.phase());
        assertEquals(105, ps.get(2).stack());
    }
}
