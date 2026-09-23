package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.card.Card;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A seated player's view of the hand, and their buttons when it is their turn.
 *
 * <p>The top row repeats the player's own cards and the board, so they can read their
 * hand while the menu covers the table. Only the player's own cards are ever put in it.
 */
final class PokerMenu extends Menu {

    private static final int[] HOLE_SLOTS = {0, 1};
    private static final int HAND_NAME_SLOT = 2;
    private static final int[] BOARD_SLOTS = {4, 5, 6, 7, 8};

    private static final int STACK_SLOT = 10;
    private static final int POT_SLOT = 13;
    private static final int TO_CALL_SLOT = 16;

    private static final int FOLD_SLOT = 27;
    private static final int CALL_SLOT = 29;
    private static final int[] RAISE_SLOTS = {31, 32, 33};
    private static final int ALL_IN_SLOT = 35;
    private static final int WAIT_SLOT = 31;
    private static final int LEAVE_SLOT = 40;

    private final PokerTable table;

    PokerMenu(CasinoPlugin plugin, Player player, PokerTable table) {
        super(plugin, player);
        this.table = table;
    }

    PokerTable table() {
        return table;
    }

    @Override
    protected Component title() {
        HoldemHand hand = table.hand();
        HandPlayer me = hand != null ? hand.player(player.getUniqueId()) : null;
        if (me != null && hand.toAct() == me) {
            return Text.mm("<dark_green>Your turn</dark_green>");
        }
        return Text.mm("<dark_aqua>Texas Hold'em</dark_aqua>");
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build() {
        HoldemHand hand = table.hand();
        HandPlayer me = hand != null ? hand.player(player.getUniqueId()) : null;
        List<Card> board = hand != null ? hand.board() : List.of();

        // Row one: my cards and the board.
        if (me != null && !me.folded()) {
            List<Card> hole = me.hole();
            for (int i = 0; i < HOLE_SLOTS.length && i < hole.size(); i++) {
                set(HOLE_SLOTS[i], Items.card(hole.get(i)));
            }
            List<Card> all = new ArrayList<>(hole);
            all.addAll(board);
            set(HAND_NAME_SLOT, Items.of(Material.NAME_TAG, "<gold>" + HandEvaluator.describe(all) + "</gold>",
                    "<gray>Your best hand so far.</gray>"));
        } else {
            String why = me != null ? "You folded this hand." : "You are dealt in from the next hand.";
            for (int slot : HOLE_SLOTS) {
                set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>No cards</dark_gray>",
                        "<gray>" + why + "</gray>"));
            }
        }
        for (int i = 0; i < BOARD_SLOTS.length; i++) {
            if (i < board.size()) {
                set(BOARD_SLOTS[i], Items.card(board.get(i)));
            } else {
                set(BOARD_SLOTS[i], Items.of(Material.BLACK_STAINED_GLASS_PANE,
                        "<dark_gray>Not dealt yet</dark_gray>"));
            }
        }

        // Row two: chips.
        set(STACK_SLOT, Items.chips(Math.max(1, table.stackOf(player.getUniqueId()) / 10),
                "<gold>Your chips: <white>" + Text.chips(table.stackOf(player.getUniqueId())) + "</white></gold>",
                "<gray>On the table in front of you.</gray>"));
        if (hand != null) {
            set(POT_SLOT, Items.of(Material.GOLD_BLOCK, "<gold>Pot: <white>" + Text.chips(hand.pot()) + "</white></gold>"));
        }

        boolean myTurn = me != null && hand.toAct() == me && table.state() == PokerTable.State.IN_HAND;
        if (myTurn) {
            actions(hand, me);
        } else {
            waiting(hand);
        }

        set(LEAVE_SLOT, Items.of(Material.OAK_DOOR, "<red>Stand up</red>",
                "<gray>Leave the table and take your chips.</gray>",
                me != null && !me.folded() && hand.phase() != HoldemHand.Phase.FINISHED
                        ? "<yellow>This folds your current hand.</yellow>" : ""),
                event -> {
                    player.closeInventory();
                    long back = plugin.games().leave(player.getUniqueId());
                    plugin.message(player, "<yellow>You stood up. <white>" + Text.chips(back)
                            + "</white> chips returned.</yellow>");
                });

        fillEmpty();
    }

    private void actions(HoldemHand hand, HandPlayer me) {
        int turn = table.turn();
        long toCall = hand.toCall(me);

        set(TO_CALL_SLOT, Items.of(Material.CLOCK, toCall > 0
                ? "<yellow>To call: <white>" + Text.chips(toCall) + "</white></yellow>"
                : "<green>Nothing to call</green>"));

        set(FOLD_SLOT, Items.of(Material.RED_CONCRETE, "<red><bold>Fold</bold></red>",
                "<gray>Give up this hand.</gray>"),
                event -> choose(turn, PokerTable.Move.FOLD, 0));

        if (hand.canCheck(me)) {
            set(CALL_SLOT, Items.of(Material.LIME_CONCRETE, "<green><bold>Check</bold></green>",
                    "<gray>Pass without betting.</gray>"),
                    event -> choose(turn, PokerTable.Move.CHECK_OR_CALL, 0));
        } else {
            boolean allIn = toCall >= me.stack();
            set(CALL_SLOT, Items.of(Material.LIME_CONCRETE, "<green><bold>" + (allIn ? "Call all in " : "Call ")
                            + Text.chips(toCall) + "</bold></green>",
                    "<gray>Match the bet.</gray>"),
                    event -> choose(turn, PokerTable.Move.CHECK_OR_CALL, 0));
        }

        if (!hand.canRaise(me)) {
            set(RAISE_SLOTS[1], Items.of(Material.GRAY_DYE, "<dark_gray>No raise</dark_gray>",
                    "<gray>You cannot raise right now.</gray>"));
            return;
        }

        long min = hand.minRaiseTo(me);
        long max = hand.maxRaiseTo(me);
        long owed = hand.currentBet() - me.bet();
        long potRaise = hand.currentBet() + hand.pot() + Math.max(0, owed);
        long halfPotRaise = hand.currentBet() + (hand.pot() + Math.max(0, owed)) / 2;
        String verb = hand.currentBet() == 0 ? "Bet" : "Raise to";

        long[] sizes = {min, halfPotRaise, potRaise};
        String[] names = {"Minimum", "Half the pot", "The pot"};
        Material[] icons = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK};
        List<Long> offered = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            long size = Math.max(min, Math.min(max, sizes[i]));
            if (size >= max || offered.contains(size)) {
                continue;
            }
            offered.add(size);
            set(RAISE_SLOTS[i], Items.of(icons[i], "<gold><bold>" + verb + " " + Text.chips(size) + "</bold></gold>",
                    "<gray>" + names[i] + ".</gray>"),
                    event -> choose(turn, PokerTable.Move.RAISE, size));
        }
        set(ALL_IN_SLOT, Items.of(Material.DIAMOND, "<aqua><bold>All in " + Text.chips(max) + "</bold></aqua>",
                "<gray>Bet everything you have.</gray>"),
                event -> choose(turn, PokerTable.Move.RAISE, max));
    }

    private void waiting(HoldemHand hand) {
        String title;
        String detail;
        if (hand != null && hand.toAct() != null) {
            title = "<yellow>Waiting for " + hand.toAct().name() + "</yellow>";
            detail = "<gray>This menu opens by itself on your turn.</gray>";
        } else if (table.state() == PokerTable.State.COUNTDOWN) {
            title = "<yellow>Next hand in " + table.countdown() + "</yellow>";
            detail = "<gray>Get ready.</gray>";
        } else if (table.state() == PokerTable.State.WAITING) {
            title = "<yellow>Waiting for players</yellow>";
            detail = "<gray>A hand starts once " + plugin.config().pokerMinPlayers() + " players sit down.</gray>";
        } else {
            title = "<gray>Dealing...</gray>";
            detail = "";
        }
        set(WAIT_SLOT, Items.of(Material.CLOCK, title, detail));
    }

    private void choose(int turn, PokerTable.Move move, long amount) {
        player.closeInventory();
        table.act(player, turn, move, amount);
    }
}
