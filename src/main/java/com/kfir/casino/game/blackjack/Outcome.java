package com.kfir.casino.game.blackjack;

/** How a settled blackjack hand finished, from the point of view of the player. */
public enum Outcome {

    PLAYER_BLACKJACK("<gold><bold>Blackjack</bold></gold>"),
    PLAYER_WIN("<green><bold>You win</bold></green>"),
    DEALER_BUST("<green><bold>Dealer busts, you win</bold></green>"),
    PUSH("<yellow><bold>Push</bold></yellow>"),
    PLAYER_BUST("<red><bold>Bust</bold></red>"),
    DEALER_WIN("<red><bold>Dealer wins</bold></red>");

    private final String display;

    Outcome(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public boolean isWin() {
        return this == PLAYER_BLACKJACK || this == PLAYER_WIN || this == DEALER_BUST;
    }
}
