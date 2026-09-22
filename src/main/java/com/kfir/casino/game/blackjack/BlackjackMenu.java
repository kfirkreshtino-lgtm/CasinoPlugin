package com.kfir.casino.game.blackjack;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.card.Card;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The blackjack table GUI.
 *
 * <p>Blackjack is single player and fully synchronous, so the whole game lives in one
 * inventory: dealer row on top, player row below, controls along the bottom.
 */
public final class BlackjackMenu extends Menu {

    private static final int INFO_SLOT = 4;
    private static final int DEALER_FIRST_CARD = 10;
    private static final int DEALER_TOTAL = 18;
    private static final int PLAYER_FIRST_CARD = 28;
    private static final int PLAYER_TOTAL = 36;
    private static final int BET_SLOT = 40;

    private static final long[] CHIP_STEPS = {5, 25, 100, 500};
    private static final int[] CHIP_SLOTS = {45, 46, 47, 48};

    private final BlackjackGame game;

    public BlackjackMenu(CasinoPlugin plugin, Player player, BlackjackGame game) {
        super(plugin, player);
        this.game = game;
        game.attachMenu(this);
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_green>Blackjack</dark_green>");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build() {
        buildInfo();
        buildDealerRow();
        buildPlayerRow();
        buildControls();
        fillEmpty();
    }

    private void buildInfo() {
        String state = switch (game.state()) {
            case BETTING -> "<yellow>Place your bet</yellow>";
            case PLAYER_TURN -> "<green>Your move</green>";
            case DEALER_TURN -> "<gold>Dealer is playing</gold>";
            case FINISHED -> game.outcome() == null ? "<gray>Hand over</gray>" : game.outcome().display();
        };
        set(INFO_SLOT, Items.of(Material.PAPER, "<gold><bold>Blackjack</bold></gold>",
                state,
                "",
                "<gray>Table limits: <white>" + Text.chips(plugin.config().blackjackMinBet())
                        + " - " + Text.chips(plugin.config().blackjackMaxBet()) + "</white> chips</gray>",
                "<gray>Blackjack pays <white>" + Text.money(plugin.config().blackjackPayout())
                        + " to 1</white></gray>"));
    }

    private void buildDealerRow() {
        List<Card> cards = game.dealerHand().cards();
        boolean hideHole = game.state() == BlackjackGame.State.PLAYER_TURN;
        for (int i = 0; i < cards.size() && i < 7; i++) {
            boolean hidden = hideHole && i == 1;
            set(DEALER_FIRST_CARD + i, hidden ? Items.faceDownCard() : Items.card(cards.get(i)));
        }
        String total = cards.isEmpty() ? "<gray>-</gray>"
                : hideHole ? "<white>" + cards.get(0).rank().blackjackValue() + " + ?</white>"
                : "<white>" + game.dealerHand().total() + "</white>"
                        + (game.dealerHand().isBust() ? " <red>bust</red>" : "");
        set(DEALER_TOTAL, Items.of(Material.IRON_HELMET, "<gray><bold>Dealer</bold></gray>", total));
    }

    private void buildPlayerRow() {
        List<Card> cards = game.playerHand().cards();
        for (int i = 0; i < cards.size() && i < 7; i++) {
            set(PLAYER_FIRST_CARD + i, Items.card(cards.get(i)));
        }
        String total = cards.isEmpty() ? "<gray>-</gray>"
                : "<white>" + game.playerHand().total() + "</white>"
                        + (game.playerHand().isSoft() ? " <aqua>soft</aqua>" : "")
                        + (game.playerHand().isBust() ? " <red>bust</red>" : "");
        set(PLAYER_TOTAL, Items.of(Material.PLAYER_HEAD, "<green><bold>" + player.getName()
                + "</bold></green>", total));

        set(BET_SLOT, Items.chips(Math.max(1, game.bet()), "<gold>Current bet: <white>"
                        + Text.chips(game.bet()) + "</white></gold>",
                "<gray>Your chips: <white>"
                        + Text.chips(plugin.chipBank().balance(player)) + "</white></gray>"));
    }

    private void buildControls() {
        switch (game.state()) {
            case BETTING -> buildBettingControls();
            case PLAYER_TURN -> buildTurnControls();
            case DEALER_TURN -> set(49, Items.of(Material.CLOCK, "<gold>Dealer is drawing</gold>",
                    "<gray>Sit tight.</gray>"));
            case FINISHED -> buildFinishedControls();
        }
    }

    private void buildBettingControls() {
        for (int i = 0; i < CHIP_STEPS.length; i++) {
            long step = CHIP_STEPS[i];
            set(CHIP_SLOTS[i], Items.chips(Math.min(64, step), "<green>+" + Text.chips(step) + "</green>",
                            "<gray>Add to your bet.</gray>"),
                    event -> {
                        game.addToBet(step);
                        refresh();
                    });
        }
        set(50, Items.of(Material.BARRIER, "<red>Clear bet</red>"), event -> {
            game.clearBet();
            refresh();
        });
        set(52, Items.of(Material.GOLD_BLOCK, "<gold>Your chips</gold>",
                "<white>" + Text.chips(plugin.chipBank().balance(player)) + "</white>",
                "",
                "<gray>Buy more at the cashier.</gray>"));
        set(53, Items.of(Material.LIME_CONCRETE, "<green><bold>Deal</bold></green>",
                "<gray>Bet <white>" + Text.chips(game.bet()) + "</white> chips.</gray>"), event -> {
            String error = game.deal();
            if (error != null) {
                plugin.message(player, error);
            }
            refresh();
        });
    }

    private void buildTurnControls() {
        set(47, Items.of(Material.LIME_CONCRETE, "<green><bold>Hit</bold></green>",
                "<gray>Take another card.</gray>"), event -> game.hit());
        set(49, Items.of(Material.ORANGE_CONCRETE, "<gold><bold>Stand</bold></gold>",
                "<gray>Keep your total and let the dealer play.</gray>"), event -> game.stand());
        if (game.canDouble()) {
            set(51, Items.of(Material.DIAMOND, "<aqua><bold>Double down</bold></aqua>",
                    "<gray>Double your bet, take exactly one card.</gray>",
                    "<gray>Costs <white>" + Text.chips(game.bet()) + "</white> more chips.</gray>"), event -> {
                String error = game.doubleDown();
                if (error != null) {
                    plugin.message(player, error);
                }
            });
        }
    }

    private void buildFinishedControls() {
        long net = game.payout() - game.bet();
        String netText = net > 0 ? "<green>+" + Text.chips(net) + " chips</green>"
                : net < 0 ? "<red>" + Text.chips(net) + " chips</red>"
                : "<yellow>Stake returned</yellow>";
        set(49, Items.of(Material.LIME_CONCRETE, "<green><bold>Play again</bold></green>", netText,
                "<gray>Your last bet is kept.</gray>"), event -> {
            game.reset();
            refresh();
        });
        set(53, Items.of(Material.BARRIER, "<red>Leave table</red>"), event -> player.closeInventory());
    }
}
