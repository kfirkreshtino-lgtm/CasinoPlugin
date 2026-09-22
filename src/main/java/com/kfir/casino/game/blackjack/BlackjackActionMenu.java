package com.kfir.casino.game.blackjack;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The only inventory a player sees during a hand, and only while it is their turn.
 *
 * <p>It is a single row holding hit, stand and double, it opens by itself when the table
 * settles and the player has a decision to make, and it closes the moment they choose. The
 * cards, totals and result all live on the table, so there is nothing else to show here.
 */
public final class BlackjackActionMenu extends Menu {

    private static final int HIT_SLOT = 2;
    private static final int STAND_SLOT = 4;
    private static final int DOUBLE_SLOT = 6;

    private final BlackjackGame game;

    public BlackjackActionMenu(CasinoPlugin plugin, Player player, BlackjackGame game) {
        super(plugin, player);
        this.game = game;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_green>Your move  <gray>|</gray>  <white>"
                + game.playerHand().total() + "</white></dark_green>");
    }

    @Override
    protected int size() {
        return 9;
    }

    @Override
    protected void build() {
        set(HIT_SLOT, Items.of(Material.LIME_CONCRETE, "<green><bold>Hit</bold></green>",
                "<gray>Take another card.</gray>"), event -> act(game::hit));

        set(STAND_SLOT, Items.of(Material.ORANGE_CONCRETE, "<gold><bold>Stand</bold></gold>",
                "<gray>Hold on <white>" + game.playerHand().total() + "</white>.</gray>"),
                event -> act(game::stand));

        if (game.canDouble()) {
            set(DOUBLE_SLOT, Items.of(Material.DIAMOND, "<aqua><bold>Double down</bold></aqua>",
                    "<gray>Double to <white>" + Text.chips(game.bet() * 2) + "</white>,</gray>",
                    "<gray>then take exactly one card.</gray>"), event -> act(() -> {
                String error = game.doubleDown();
                if (error != null) {
                    plugin.message(player, error);
                }
            }));
        } else {
            set(DOUBLE_SLOT, Items.of(Material.GRAY_DYE, "<dark_gray>Double unavailable</dark_gray>",
                    "<gray>Only on your first two cards,</gray>",
                    "<gray>and only with enough chips.</gray>"));
        }

        fillEmpty();
    }

    /** Close first, then act, so the table animation is never hidden behind an inventory. */
    private void act(Runnable action) {
        player.closeInventory();
        action.run();
    }
}
