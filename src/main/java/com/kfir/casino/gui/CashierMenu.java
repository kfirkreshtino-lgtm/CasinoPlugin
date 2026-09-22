package com.kfir.casino.gui;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.economy.ExchangeResult;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Buy and sell chips. Exact amounts go through the chips command instead. */
public final class CashierMenu extends Menu {

    private static final long[] AMOUNTS = {10, 50, 100, 500, 1000};
    private static final int[] BUY_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] SELL_SLOTS = {29, 30, 31, 32, 33};

    public CashierMenu(CasinoPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_green>Casino Cashier</dark_green>");
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build() {
        long chips = plugin.chipBank().balance(player);
        double money = plugin.chipBank().vault().balance(player);
        double price = plugin.config().chipPrice();

        set(4, Items.of(Material.EMERALD, "<gold><bold>Your balances</bold></gold>",
                "<gray>Chips: <white>" + Text.chips(chips) + "</white></gray>",
                "<gray>Money: <white>" + plugin.chipBank().vault().format(money) + "</white></gray>",
                "",
                "<gray>1 chip costs <white>" + Text.money(price) + "</white></gray>",
                "<gray>Sell fee: <white>" + Text.money(plugin.config().sellFeePercent()) + "%</white></gray>"));

        set(9, Items.of(Material.LIME_STAINED_GLASS_PANE, "<green><bold>Buy chips</bold></green>",
                "<gray>Spend money, receive chips.</gray>"));
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            double cost = amount * price;
            set(BUY_SLOTS[i], Items.of(Material.GOLD_INGOT, (int) Math.min(64, amount),
                            "<green>Buy " + Text.chips(amount) + " chips</green>",
                            "<gray>Costs <white>" + plugin.chipBank().vault().format(cost) + "</white></gray>"),
                    event -> {
                        ExchangeResult result = plugin.chipBank().buy(player, amount);
                        finish(result);
                    });
        }

        set(27, Items.of(Material.RED_STAINED_GLASS_PANE, "<red><bold>Sell chips</bold></red>",
                "<gray>Cash chips back into money.</gray>"));
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            double gross = amount * price;
            double payout = gross * (1.0 - plugin.config().sellFeePercent() / 100.0);
            set(SELL_SLOTS[i], Items.of(Material.GOLD_NUGGET, (int) Math.min(64, amount),
                            "<red>Sell " + Text.chips(amount) + " chips</red>",
                            "<gray>Pays <white>" + plugin.chipBank().vault().format(payout) + "</white></gray>"),
                    event -> {
                        ExchangeResult result = plugin.chipBank().sell(player, amount);
                        finish(result);
                    });
        }

        set(40, Items.of(Material.BOOK, "<yellow>Exact amounts</yellow>",
                "<gray>Use <white>/casino chips buy 1234</white></gray>",
                "<gray>or <white>/casino chips sell 1234</white></gray>"));

        fillEmpty();
    }

    private void finish(ExchangeResult result) {
        plugin.message(player, result.message());
        player.playSound(player.getLocation(),
                result.success() ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        refresh();
    }
}
