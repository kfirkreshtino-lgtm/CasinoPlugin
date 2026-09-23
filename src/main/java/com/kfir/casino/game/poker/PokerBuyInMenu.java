package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoConfig;
import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Choose how many chips to bring to the poker table. */
final class PokerBuyInMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    private final PokerTable table;

    PokerBuyInMenu(CasinoPlugin plugin, Player player, PokerTable table) {
        super(plugin, player);
        this.table = table;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_aqua>Texas Hold'em  <gray>|</gray>  Buy in</dark_aqua>");
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build() {
        CasinoConfig config = plugin.config();
        long balance = plugin.chipBank().balance(player);
        long min = config.pokerMinBuyIn();
        long max = config.pokerMaxBuyIn();

        set(4, Items.of(Material.EMERALD, "<gold><bold>Sit down</bold></gold>",
                "<gray>Your chips: <white>" + Text.chips(balance) + "</white></gray>",
                "<gray>Blinds: <white>" + Text.chips(config.pokerSmallBlind()) + "/"
                        + Text.chips(config.pokerBigBlind()) + "</white></gray>",
                "",
                "<gray>Chips you bring stay on the table</gray>",
                "<gray>until you stand up.</gray>"));

        List<Long> amounts = new ArrayList<>();
        for (long amount : new long[]{min, min * 2, min * 5, max, Math.min(balance, max)}) {
            if (amount >= min && amount <= max && !amounts.contains(amount)) {
                amounts.add(amount);
            }
        }
        amounts.sort(Long::compare);

        for (int i = 0; i < amounts.size() && i < SLOTS.length; i++) {
            long amount = amounts.get(i);
            if (amount > balance) {
                set(SLOTS[i], Items.of(Material.GRAY_DYE, "<dark_gray>Bring " + Text.chips(amount) + " chips</dark_gray>",
                        "<red>You do not have enough chips.</red>"));
                continue;
            }
            set(SLOTS[i], Items.chips(amount, "<green>Bring <white>" + Text.chips(amount) + "</white> chips</green>",
                            "<gray>" + amount / config.pokerBigBlind() + " big blinds</gray>"),
                    event -> {
                        player.closeInventory();
                        table.sit(player, amount);
                    });
        }
        fillEmpty();
    }
}
