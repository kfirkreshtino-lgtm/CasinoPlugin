package com.kfir.casino.economy;

/**
 * Outcome of a chip buy or cash out.
 *
 * @param success  whether chips actually moved
 * @param message  MiniMessage text to show the player
 * @param chips    chips added to or removed from the player
 * @param diamonds diamonds moved in the opposite direction
 */
public record ExchangeResult(boolean success, String message, long chips, int diamonds) {

    public static ExchangeResult fail(String message) {
        return new ExchangeResult(false, message, 0, 0);
    }

    public static ExchangeResult ok(String message, long chips, int diamonds) {
        return new ExchangeResult(true, message, chips, diamonds);
    }
}
