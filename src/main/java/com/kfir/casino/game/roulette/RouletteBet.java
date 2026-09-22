package com.kfir.casino.game.roulette;

/**
 * A single wager inside a round.
 *
 * @param type   which bet was placed
 * @param number the chosen pocket, only meaningful for a straight-up bet
 * @param amount chips staked
 */
public record RouletteBet(BetType type, int number, long amount) {

    public static RouletteBet straight(int number, long amount) {
        return new RouletteBet(BetType.STRAIGHT, number, amount);
    }

    public static RouletteBet outside(BetType type, long amount) {
        return new RouletteBet(type, -1, amount);
    }

    /** Whether this bet wins against the given result. Zero loses every outside bet. */
    public boolean wins(int result, Wheel wheel) {
        if (type == BetType.STRAIGHT) {
            return number == result;
        }
        if (wheel.isGreen(result)) {
            return false;
        }
        return switch (type) {
            case RED -> wheel.isRed(result);
            case BLACK -> wheel.isBlack(result);
            case ODD -> result % 2 == 1;
            case EVEN -> result % 2 == 0;
            case LOW -> result >= 1 && result <= 18;
            case HIGH -> result >= 19 && result <= 36;
            case DOZEN_1 -> result >= 1 && result <= 12;
            case DOZEN_2 -> result >= 13 && result <= 24;
            case DOZEN_3 -> result >= 25 && result <= 36;
            case COLUMN_1 -> result % 3 == 1;
            case COLUMN_2 -> result % 3 == 2;
            case COLUMN_3 -> result % 3 == 0;
            case STRAIGHT -> false;
        };
    }

    /** Chips returned when this bet wins, stake included. */
    public long payout() {
        return amount * (type.ratio() + 1L);
    }

    public String describe(Wheel wheel) {
        if (type == BetType.STRAIGHT) {
            return "Straight up on " + wheel.label(number);
        }
        return type.displayName();
    }
}
