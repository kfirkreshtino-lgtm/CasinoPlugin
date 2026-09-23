package com.kfir.casino.gui;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.economy.ExchangeResult;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Trade diamonds for chips and back. Exact amounts go through the chips command instead. */
public final class CashierMenu extends Menu {

    private static final int[] DIAMONDS = {1, 8, 16, 32, 64};
    private static final int[] BUY_SLOTS = {11, 12, 13, 14, 15};
    private static final int BUY_ALL_SLOT = 16;
    private static final int[] SELL_SLOTS = {29, 30, 31, 32, 33};
    private static final int SELL_ALL_SLOT = 34;

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
        int held = plugin.chipBank().diamonds(player);
        int rate = plugin.config().chipsPerDiamond();

        set(4, Items.of(Material.EMERALD, "<gold><bold>Your balances</bold></gold>",
                "<gray>Chips: <white>" + Text.chips(chips) + "</white></gray>",
                "<gray>Diamonds: <aqua>" + held + "</aqua></gray>",
                "",
                "<gray>1 diamond = <white>" + Text.chips(rate) + "</white> chips</gray>"));

        set(9, Items.of(Material.LIME_STAINED_GLASS_PANE, "<green><bold>Buy chips</bold></green>",
                "<gray>Pay diamonds, receive chips.</gray>"));
        for (int i = 0; i < DIAMONDS.length; i++) {
            int diamonds = DIAMONDS[i];
            set(BUY_SLOTS[i], Items.of(Material.DIAMOND, diamonds,
                            "<green>Buy " + Text.chips((long) diamonds * rate) + " chips</green>",
                            "<gray>Costs <aqua>" + diamonds + " " + plural(diamonds) + "</aqua></gray>"),
                    event -> finish(plugin.chipBank().buy(player, diamonds)));
        }
        int buyAll = Math.min(held, plugin.config().maxExchangeDiamonds());
        set(BUY_ALL_SLOT, Items.of(Material.DIAMOND_BLOCK, "<green><bold>Buy with all diamonds</bold></green>",
                        "<gray>Trades <aqua>" + buyAll + " " + plural(buyAll) + "</aqua> for <white>"
                                + Text.chips((long) buyAll * rate) + "</white> chips</gray>"),
                event -> finish(plugin.chipBank().buy(player,
                        Math.min(plugin.chipBank().diamonds(player), plugin.config().maxExchangeDiamonds()))));

        set(27, Items.of(Material.RED_STAINED_GLASS_PANE, "<red><bold>Cash out</bold></red>",
                "<gray>Turn chips back into diamonds.</gray>"));
        for (int i = 0; i < DIAMONDS.length; i++) {
            int diamonds = DIAMONDS[i];
            set(SELL_SLOTS[i], Items.of(Material.GOLD_NUGGET, diamonds,
                            "<red>Cash out for " + diamonds + " " + plural(diamonds) + "</red>",
                            "<gray>Costs <white>" + Text.chips((long) diamonds * rate) + "</white> chips</gray>"),
                    event -> finish(plugin.chipBank().sell(player, diamonds)));
        }
        int sellAll = plugin.chipBank().diamondsForChips(player);
        set(SELL_ALL_SLOT, Items.of(Material.GOLD_BLOCK, "<red><bold>Cash out everything</bold></red>",
                        "<gray>Pays <aqua>" + sellAll + " " + plural(sellAll) + "</aqua> for <white>"
                                + Text.chips((long) sellAll * rate) + "</white> chips</gray>",
                        "<dark_gray>Chips worth less than a diamond stay on your balance.</dark_gray>"),
                event -> finish(plugin.chipBank().sell(player, plugin.chipBank().diamondsForChips(player))));

        set(40, Items.of(Material.BOOK, "<yellow>Exact amounts</yellow>",
                "<gray>Use <white>/casino chips buy 5</white></gray>",
                "<gray>or <white>/casino chips sell 5</white></gray>",
                "<gray>The number is diamonds.</gray>"));

        fillEmpty();
    }

    private static String plural(int diamonds) {
        return diamonds == 1 ? "diamond" : "diamonds";
    }

    private void finish(ExchangeResult result) {
        plugin.message(player, result.message());
        player.playSound(player.getLocation(),
                result.success() ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        refresh();
    }
}
