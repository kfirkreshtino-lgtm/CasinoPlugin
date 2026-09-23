package com.kfir.casino.game.blackjack;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.card.Card;
import com.kfir.casino.table.CardVisual;
import com.kfir.casino.table.Hologram;
import com.kfir.casino.table.TableLayout;
import com.kfir.casino.util.Tasks;
import com.kfir.casino.util.Text;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Draws a blackjack game on a real table in the world.
 *
 * <p>Cards are display entities that slide out of the shoe and turn over where they land.
 * Totals, the bet and the result float over the felt. Nothing about the game lives in an
 * inventory, so the player watches the table rather than a chest.
 *
 * <p>Animation is queued rather than immediate. Dealing four cards at once would look like
 * they all appeared together, so each step is popped off a queue and holds the table for
 * its own number of ticks before the next one runs. Anything that must wait for the table
 * to settle is queued behind a pause long enough for the last card to land, turn over and
 * be read.
 */
public final class BlackjackTableView implements BlackjackView {

    /** Ticks between two dealt cards. */
    private static final int STAGGER = 12;
    /** How long a card takes to slide out of the shoe. */
    private static final int SLIDE_TICKS = 10;
    /** How long a card takes to turn over. */
    private static final int FLIP_TICKS = 8;
    /**
     * Pause after the table stops moving before anything that waits for it, such as the
     * action menu, which covers the screen. Long enough to read the cards first.
     */
    private static final int READ_TICKS = 30;

    private final CasinoPlugin plugin;
    private final BlackjackGame game;
    private final Player player;
    private final TableLayout layout;

    private final List<CardVisual> playerCards = new ArrayList<>();
    private final List<CardVisual> dealerCards = new ArrayList<>();
    private final Deque<Step> animations = new ArrayDeque<>();

    private Hologram playerTotal;
    private Hologram dealerTotal;
    private Hologram status;
    private Hologram betLabel;

    private BukkitTask pump;

    /** One queued animation step and how long the table is busy after it runs. */
    private record Step(Runnable action, int holdTicks) {
    }

    public BlackjackTableView(CasinoPlugin plugin, BlackjackGame game, Player player, TableLayout layout) {
        this.plugin = plugin;
        this.game = game;
        this.player = player;
        this.layout = layout;
        spawnLabels();
    }

    private void spawnLabels() {
        dealerTotal = Hologram.spawn(layout.handLabel(true), "<gray>Dealer</gray>", 0.7f);
        playerTotal = Hologram.spawn(layout.handLabel(false), "<gray>" + player.getName() + "</gray>", 0.7f);
        status = Hologram.spawn(layout.statusLabel(), "<yellow>Place your bet</yellow>", 0.9f);
        betLabel = Hologram.spawn(layout.betSpot(), "<gold>No bet</gold>", 0.55f);
    }

    // ------------------------------------------------------------ view hooks

    @Override
    public void render() {
        if (dealerTotal == null) {
            return;
        }
        dealerTotal.setText(dealerLabel());
        playerTotal.setText(playerLabel());
        status.setText(statusLabel());
        betLabel.setText(game.bet() > 0
                ? "<gold>Bet <white>" + Text.chips(game.bet()) + "</white></gold>"
                : "<dark_gray>No bet</dark_gray>");
    }

    private String dealerLabel() {
        if (game.dealerHand().size() == 0) {
            return "<gray>Dealer</gray>";
        }
        if (game.state() == BlackjackGame.State.PLAYER_TURN) {
            List<Card> cards = game.dealerHand().cards();
            return "<gray>Dealer</gray> <white>" + cards.get(0).rank().blackjackValue() + " + ?</white>";
        }
        String total = "<white>" + game.dealerHand().total() + "</white>";
        if (game.dealerHand().isBust()) {
            total += " <red>bust</red>";
        }
        return "<gray>Dealer</gray> " + total;
    }

    private String playerLabel() {
        if (game.playerHand().size() == 0) {
            return "<gray>" + player.getName() + "</gray>";
        }
        String total = "<white>" + game.playerHand().total() + "</white>";
        if (game.playerHand().isSoft()) {
            total += " <aqua>soft</aqua>";
        }
        if (game.playerHand().isBust()) {
            total += " <red>bust</red>";
        }
        return "<green>" + player.getName() + "</green> " + total;
    }

    private String statusLabel() {
        return switch (game.state()) {
            case BETTING -> "<yellow>Place your bet</yellow>";
            case PLAYER_TURN -> "<green>Your move</green>";
            case DEALER_TURN -> "<gold>Dealer draws</gold>";
            case FINISHED -> game.outcome() == null ? "<gray>Hand over</gray>" : game.outcome().display();
        };
    }

    // ------------------------------------------------------------- animation

    @Override
    public void dealt(boolean dealer) {
        List<CardVisual> row = dealer ? dealerCards : playerCards;
        int index = row.size();
        CardVisual visual = CardVisual.spawn(layout.shoe(), layout.yaw());
        row.add(visual);

        enqueue(() -> {
            layoutRow(dealer);
            playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.4f);
            Card card = cardAt(dealer, index);
            boolean hole = dealer && index == 1;
            if (card != null && !hole) {
                Tasks.later(plugin, SLIDE_TICKS, () -> visual.reveal(card, FLIP_TICKS));
            }
            render();
        }, STAGGER);
    }

    @Override
    public void revealHole() {
        enqueue(() -> {
            if (dealerCards.size() > 1) {
                Card hole = cardAt(true, 1);
                if (hole != null && !dealerCards.get(1).isFaceUp()) {
                    dealerCards.get(1).reveal(hole, FLIP_TICKS);
                    playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.1f);
                }
            }
            render();
        }, STAGGER);
    }

    @Override
    public void finished() {
        enqueue(() -> {
            render();
            Outcome outcome = game.outcome();
            if (outcome == null) {
                return;
            }
            long net = game.payout() - game.bet();
            String suffix = net > 0 ? " <green>+" + Text.chips(net) + "</green>"
                    : net < 0 ? " <red>" + Text.chips(net) + "</red>"
                    : "";
            status.setText(outcome.display() + suffix);
            playSound(outcome.isWin() ? Sound.ENTITY_PLAYER_LEVELUP
                    : outcome == Outcome.PUSH ? Sound.BLOCK_NOTE_BLOCK_PLING
                    : Sound.ENTITY_VILLAGER_NO, 1f);
        }, STAGGER);
    }

    @Override
    public void reset() {
        enqueue(() -> {
            clearCards();
            render();
        }, STAGGER);
    }

    @Override
    public void whenSettled(Runnable action) {
        // The last card may still be sliding and turning over, so wait for that to finish
        // and then give the player a moment to read the table.
        enqueue(() -> { }, SLIDE_TICKS + FLIP_TICKS + READ_TICKS);
        enqueue(action, 0);
    }

    /** Moves every card in a row to its resting place, re-centring the row as it grows. */
    private void layoutRow(boolean dealer) {
        List<CardVisual> row = dealer ? dealerCards : playerCards;
        for (int i = 0; i < row.size(); i++) {
            Location slot = layout.cardSlot(i, row.size(), dealer);
            row.get(i).moveTo(slot, SLIDE_TICKS);
        }
    }

    private Card cardAt(boolean dealer, int index) {
        List<Card> cards = dealer ? game.dealerHand().cards() : game.playerHand().cards();
        return index < cards.size() ? cards.get(index) : null;
    }

    private void enqueue(Runnable action, int holdTicks) {
        animations.add(new Step(action, holdTicks));
        if (pump == null) {
            pump = Tasks.later(plugin, 0L, this::runNext);
        }
    }

    /** Runs one queued step, then waits out its hold before the next, stopping when empty. */
    private void runNext() {
        Step next = animations.poll();
        if (next == null) {
            pump = null;
            return;
        }
        next.action().run();
        pump = Tasks.later(plugin, Math.max(1, next.holdTicks()), this::runNext);
    }

    private void playSound(Sound sound, float pitch) {
        if (player.isOnline()) {
            player.playSound(layout.statusLabel(), sound, 0.7f, pitch);
        }
    }

    private void clearCards() {
        playerCards.forEach(CardVisual::remove);
        dealerCards.forEach(CardVisual::remove);
        playerCards.clear();
        dealerCards.clear();
    }

    @Override
    public void close() {
        Tasks.cancel(pump);
        pump = null;
        animations.clear();
        clearCards();
        if (dealerTotal != null) {
            dealerTotal.remove();
            playerTotal.remove();
            status.remove();
            betLabel.remove();
            dealerTotal = null;
        }
    }
}
