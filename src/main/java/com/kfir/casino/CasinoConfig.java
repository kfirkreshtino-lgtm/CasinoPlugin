package com.kfir.casino;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Typed view over config.yml. Rebuilt on every reload so nothing holds a stale value. */
public final class CasinoConfig {

    private final Vector structureOffset;
    private final boolean spawnLabels;

    private final int chipsPerDiamond;
    private final int maxExchangeDiamonds;

    private final long blackjackMinBet;
    private final long blackjackMaxBet;
    private final int blackjackDecks;
    private final double blackjackReshuffleAt;
    private final double blackjackPayout;
    private final boolean dealerHitsSoft17;
    private final int dealDelayTicks;

    private final long rouletteMinBet;
    private final long rouletteMaxBet;
    private final String rouletteWheel;
    private final int betWindowSeconds;
    private final int spinTicks;
    private final List<Long> stakeSteps;

    private final long pokerSmallBlind;
    private final long pokerBigBlind;
    private final long pokerMinBuyIn;
    private final long pokerMaxBuyIn;
    private final int pokerMinPlayers;
    private final int pokerStartDelaySeconds;
    private final int pokerTurnSeconds;
    private final int autosaveSeconds;
    private final String prefix;

    public CasinoConfig(FileConfiguration c) {
        this.structureOffset = new Vector(
                c.getInt("structure.offset.x", 12),
                c.getInt("structure.offset.y", 0),
                c.getInt("structure.offset.z", 12));
        this.spawnLabels = c.getBoolean("structure.spawn-labels", true);

        this.chipsPerDiamond = Math.max(1, c.getInt("economy.chips-per-diamond", 10));
        this.maxExchangeDiamonds = Math.max(1, c.getInt("economy.max-exchange-diamonds", 640));

        this.blackjackMinBet = Math.max(1, c.getLong("blackjack.min-bet", 5));
        this.blackjackMaxBet = Math.max(blackjackMinBet, c.getLong("blackjack.max-bet", 500));
        this.blackjackDecks = Math.max(1, c.getInt("blackjack.decks", 6));
        this.blackjackReshuffleAt = c.getDouble("blackjack.reshuffle-at", 0.25);
        this.blackjackPayout = Math.max(0.0, c.getDouble("blackjack.blackjack-payout", 1.5));
        this.dealerHitsSoft17 = c.getBoolean("blackjack.dealer-hits-soft-17", false);
        this.dealDelayTicks = Math.max(1, c.getInt("blackjack.deal-delay-ticks", 14));

        this.rouletteMinBet = Math.max(1, c.getLong("roulette.min-bet", 5));
        this.rouletteMaxBet = Math.max(rouletteMinBet, c.getLong("roulette.max-bet", 500));
        this.rouletteWheel = c.getString("roulette.wheel", "EUROPEAN");
        this.betWindowSeconds = Math.max(5, c.getInt("roulette.bet-window-seconds", 30));
        this.spinTicks = Math.max(20, c.getInt("roulette.spin-ticks", 60));

        List<Long> steps = new ArrayList<>();
        for (int value : c.getIntegerList("roulette.stake-steps")) {
            if (value > 0) {
                steps.add((long) value);
            }
        }
        if (steps.isEmpty()) {
            steps = List.of(5L, 10L, 25L, 50L, 100L, 250L);
        }
        this.stakeSteps = List.copyOf(steps);

        this.pokerSmallBlind = Math.max(1, c.getLong("poker.small-blind", 5));
        this.pokerBigBlind = Math.max(pokerSmallBlind, c.getLong("poker.big-blind", 10));
        this.pokerMinBuyIn = Math.max(pokerBigBlind, c.getLong("poker.min-buy-in", 100));
        this.pokerMaxBuyIn = Math.max(pokerMinBuyIn, c.getLong("poker.max-buy-in", 1000));
        this.pokerMinPlayers = Math.min(6, Math.max(2, c.getInt("poker.min-players", 2)));
        this.pokerStartDelaySeconds = Math.max(3, c.getInt("poker.start-delay-seconds", 10));
        this.pokerTurnSeconds = Math.max(10, c.getInt("poker.turn-seconds", 30));
        this.autosaveSeconds = Math.max(15, c.getInt("storage.autosave-seconds", 120));
        this.prefix = c.getString("messages.prefix", "<gold>[Casino] </gold>");
    }

    public Vector structureOffset() {
        return structureOffset.clone();
    }

    public boolean spawnLabels() {
        return spawnLabels;
    }

    public int chipsPerDiamond() {
        return chipsPerDiamond;
    }

    public int maxExchangeDiamonds() {
        return maxExchangeDiamonds;
    }

    public long blackjackMinBet() {
        return blackjackMinBet;
    }

    public long blackjackMaxBet() {
        return blackjackMaxBet;
    }

    public int blackjackDecks() {
        return blackjackDecks;
    }

    public double blackjackReshuffleAt() {
        return blackjackReshuffleAt;
    }

    public double blackjackPayout() {
        return blackjackPayout;
    }

    public boolean dealerHitsSoft17() {
        return dealerHitsSoft17;
    }

    public int dealDelayTicks() {
        return dealDelayTicks;
    }

    public long rouletteMinBet() {
        return rouletteMinBet;
    }

    public long rouletteMaxBet() {
        return rouletteMaxBet;
    }

    public String rouletteWheel() {
        return rouletteWheel;
    }

    public int betWindowSeconds() {
        return betWindowSeconds;
    }

    public int spinTicks() {
        return spinTicks;
    }

    public List<Long> stakeSteps() {
        return stakeSteps;
    }

    public long pokerSmallBlind() {
        return pokerSmallBlind;
    }

    public long pokerBigBlind() {
        return pokerBigBlind;
    }

    public long pokerMinBuyIn() {
        return pokerMinBuyIn;
    }

    public long pokerMaxBuyIn() {
        return pokerMaxBuyIn;
    }

    /** Players needed before a hand is dealt, never below two. */
    public int pokerMinPlayers() {
        return pokerMinPlayers;
    }

    public int pokerStartDelaySeconds() {
        return pokerStartDelaySeconds;
    }

    public int pokerTurnSeconds() {
        return pokerTurnSeconds;
    }

    public int autosaveSeconds() {
        return autosaveSeconds;
    }

    public String prefix() {
        return prefix;
    }
}
