package com.kfir.casino.economy;

import com.kfir.casino.CasinoConfig;
import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.storage.ChipStore;
import com.kfir.casino.util.Text;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Chips are a balance separate from Vault money.
 *
 * <p>Design note: games never touch Vault directly. Buying chips withdraws currency once,
 * cashing out deposits it once, and every bet moves chips only. That keeps a failed economy
 * transaction from ever landing in the middle of a dealt hand.
 */
public final class ChipBank {

    private final CasinoPlugin plugin;
    private final VaultHook vault;
    private final ChipStore store;

    public ChipBank(CasinoPlugin plugin, VaultHook vault, ChipStore store) {
        this.plugin = plugin;
        this.vault = vault;
        this.store = store;
    }

    public long balance(UUID playerId) {
        return store.get(playerId);
    }

    public long balance(OfflinePlayer player) {
        return store.get(player.getUniqueId());
    }

    /** Removes chips if the player has them. Returns false and changes nothing otherwise. */
    public boolean take(UUID playerId, long chips) {
        if (chips <= 0) {
            return true;
        }
        long current = store.get(playerId);
        if (current < chips) {
            return false;
        }
        store.set(playerId, current - chips);
        return true;
    }

    public void give(UUID playerId, long chips) {
        if (chips <= 0) {
            return;
        }
        store.set(playerId, store.get(playerId) + chips);
    }

    /** Buys chips with Vault currency. */
    public ExchangeResult buy(OfflinePlayer player, long chips) {
        CasinoConfig config = plugin.config();
        if (chips <= 0) {
            return ExchangeResult.fail("<red>Enter a chip amount above zero.</red>");
        }
        if (chips > config.maxExchange()) {
            return ExchangeResult.fail("<red>You cannot buy more than " + Text.chips(config.maxExchange())
                    + " chips at once.</red>");
        }
        double cost = chips * config.chipPrice();
        if (!vault.has(player, cost)) {
            return ExchangeResult.fail("<red>You need " + vault.format(cost) + " but only have "
                    + vault.format(vault.balance(player)) + ".</red>");
        }
        if (!vault.withdraw(player, cost)) {
            return ExchangeResult.fail("<red>The economy plugin refused the withdrawal.</red>");
        }
        give(player.getUniqueId(), chips);
        return ExchangeResult.ok("<green>Bought <white>" + Text.chips(chips) + "</white> chips for <white>"
                + vault.format(cost) + "</white>.</green>", chips, cost);
    }

    /** Sells chips back for Vault currency, minus the configured house fee. */
    public ExchangeResult sell(OfflinePlayer player, long chips) {
        CasinoConfig config = plugin.config();
        if (chips <= 0) {
            return ExchangeResult.fail("<red>Enter a chip amount above zero.</red>");
        }
        if (chips > config.maxExchange()) {
            return ExchangeResult.fail("<red>You cannot sell more than " + Text.chips(config.maxExchange())
                    + " chips at once.</red>");
        }
        if (!take(player.getUniqueId(), chips)) {
            return ExchangeResult.fail("<red>You only have " + Text.chips(balance(player)) + " chips.</red>");
        }
        double gross = chips * config.chipPrice();
        double payout = gross * (1.0 - config.sellFeePercent() / 100.0);
        if (!vault.deposit(player, payout)) {
            give(player.getUniqueId(), chips);
            return ExchangeResult.fail("<red>The economy plugin refused the deposit. Your chips were returned.</red>");
        }
        return ExchangeResult.ok("<green>Sold <white>" + Text.chips(chips) + "</white> chips for <white>"
                + vault.format(payout) + "</white>.</green>", chips, payout);
    }

    public VaultHook vault() {
        return vault;
    }
}
