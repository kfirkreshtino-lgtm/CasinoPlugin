package com.kfir.casino.game.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteBetTest {

    private final Wheel european = new Wheel("EUROPEAN");
    private final Wheel american = new Wheel("AMERICAN");

    @Test
    void europeanWheelHasThirtySevenPockets() {
        assertEquals(37, european.pockets().size());
        assertEquals(38, american.pockets().size());
    }

    @Test
    void knowsPocketColours() {
        assertTrue(european.isRed(1));
        assertTrue(european.isBlack(2));
        assertTrue(european.isGreen(0));
        assertTrue(american.isGreen(Wheel.DOUBLE_ZERO));
        assertEquals("00", american.label(Wheel.DOUBLE_ZERO));
    }

    @Test
    void straightUpPaysThirtyFiveToOne() {
        RouletteBet bet = RouletteBet.straight(17, 10);
        assertTrue(bet.wins(17, european));
        assertFalse(bet.wins(18, european));
        assertEquals(360, bet.payout());
    }

    @Test
    void evenMoneyBetsPayOneToOne() {
        RouletteBet red = RouletteBet.outside(BetType.RED, 50);
        assertTrue(red.wins(1, european));
        assertFalse(red.wins(2, european));
        assertEquals(100, red.payout());
    }

    @Test
    void zeroLosesEveryOutsideBet() {
        for (BetType type : BetType.values()) {
            if (type == BetType.STRAIGHT) {
                continue;
            }
            assertFalse(RouletteBet.outside(type, 10).wins(0, european),
                    type + " should lose on zero");
            assertFalse(RouletteBet.outside(type, 10).wins(Wheel.DOUBLE_ZERO, american),
                    type + " should lose on double zero");
        }
    }

    @Test
    void straightUpOnZeroStillWins() {
        assertTrue(RouletteBet.straight(0, 10).wins(0, european));
    }

    @Test
    void dozensCoverTwelveNumbersEach() {
        assertTrue(RouletteBet.outside(BetType.DOZEN_1, 10).wins(12, european));
        assertFalse(RouletteBet.outside(BetType.DOZEN_1, 10).wins(13, european));
        assertTrue(RouletteBet.outside(BetType.DOZEN_2, 10).wins(24, european));
        assertTrue(RouletteBet.outside(BetType.DOZEN_3, 10).wins(36, european));
        assertEquals(30, RouletteBet.outside(BetType.DOZEN_1, 10).payout());
    }

    @Test
    void columnsSplitByRemainder() {
        assertTrue(RouletteBet.outside(BetType.COLUMN_1, 10).wins(34, european));
        assertTrue(RouletteBet.outside(BetType.COLUMN_2, 10).wins(35, european));
        assertTrue(RouletteBet.outside(BetType.COLUMN_3, 10).wins(36, european));
    }

    @Test
    void highLowAndParitySplitCorrectly() {
        assertTrue(RouletteBet.outside(BetType.LOW, 10).wins(18, european));
        assertFalse(RouletteBet.outside(BetType.LOW, 10).wins(19, european));
        assertTrue(RouletteBet.outside(BetType.HIGH, 10).wins(19, european));
        assertTrue(RouletteBet.outside(BetType.ODD, 10).wins(7, european));
        assertTrue(RouletteBet.outside(BetType.EVEN, 10).wins(8, european));
    }

    @Test
    void everyPocketIsExactlyOneColour() {
        for (int pocket : european.pockets()) {
            int colours = (european.isRed(pocket) ? 1 : 0)
                    + (european.isBlack(pocket) ? 1 : 0)
                    + (european.isGreen(pocket) ? 1 : 0);
            assertEquals(1, colours, "pocket " + pocket + " has " + colours + " colours");
        }
    }
}
