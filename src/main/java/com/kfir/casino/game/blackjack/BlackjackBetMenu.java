package com.kfir.casino.game.blackjack;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Chip selection before a hand starts.
 *
 * <p>This is the one point where an inventory is genuinely the right tool, because the
 * player is choosing an amount rather than watching the table. Once they press deal it
 * closes and does not come back until the hand is over.
 */
public final class BlackjackBetMenu extends Menu {

    private static final long[] CHIP_STEPS = {5, 25, 100, 500};
    private static final int[] CHIP_SLOTS = {10, 11, 12, 13};
    private static final int CLEAR_SLOT = 15;
    private static final int BET_SLOT = 4;
    private static final int DEAL_SLOT = 22;

    private final BlackjackGame game;

    public BlackjackBetMenu(CasinoPlugin plugin, Player player, BlackjackGame game) {
        super(plugin, player);
        this.game = game;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_green>Blackjack  <gray>|</gray>  Place your bet</dark_green>");
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build() {
        long balance = plugin.chipBank().balance(player);

        set(BET_SLOT, Items.chips(Math.max(1, game.bet()),
                "<gold><bold>Bet: <white>" + Text.chips(game.bet()) + "</white></bold></gold>",
                "<gray>Your chips: <white>" + Text.chips(balance) + "</white></gray>",
                "<gray>Limits: <white>" + Text.chips(plugin.config().blackjackMinBet()) + " - "
                        + Text.chips(plugin.config().blackjackMaxBet()) + "</white></gray>"));

        for (int i = 0; i < CHIP_STEPS.length; i++) {
            long step = CHIP_STEPS[i];
            set(CHIP_SLOTS[i], Items.chips(Math.min(64, step),
                    "<green>+" + Text.chips(step) + "</green>",
                    "<gray>Add to your bet.</gray>"), event -> {
                game.addToBet(step);
                refresh();
            });
        }

        set(CLEAR_SLOT, Items.of(Material.BARRIER, "<red>Clear bet</red>"), event -> {
            game.clearBet();
            refresh();
        });

        set(DEAL_SLOT, Items.of(Material.LIME_CONCRETE, "<green><bold>Deal</bold></green>",
                "<gray>Stake <white>" + Text.chips(game.bet()) + "</white> chips.</gray>"), event -> {
            String error = game.deal();
            if (error != null) {
                plugin.message(player, error);
                refresh();
                return;
            }
            player.closeInventory();
        });

        fillEmpty();
    }
}
