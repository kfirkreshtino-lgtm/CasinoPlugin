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
 * <p>Cards are display entities that simply appear in their place on the felt, with no
 * sliding or turning over. Totals, the bet and the result float over the felt.
 *
 * <p>Changes to the table still go through a queue so they happen in the order the game
 * reported them. Anything that must wait for the table, such as the action menu that
 * covers the screen, is queued behind a short pause so the player can read the cards first.
 */
public final class BlackjackTableView implements BlackjackView {

    /** Ticks between two queued table changes. */
    private static final int STEP_TICKS = 1;
    /**
     * Pause after the cards are on the table before anything that waits for it, such as the
     * action menu, which covers the screen. Long enough to read the cards first.
     */
    private static final int READ_TICKS = 20;

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

    /** One queued table change and how long to wait after it before the next. */
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
        enqueue(() -> {
            List<CardVisual> row = dealer ? dealerCards : playerCards;
            int index = row.size();
            Card card = cardAt(dealer, index);
            boolean hole = dealer && index == 1;
            row.add(CardVisual.place(layout.cardSlot(index, index + 1, dealer), hole ? null : card));
            layoutRow(dealer);
            playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.4f);
            render();
        }, STEP_TICKS);
    }

    @Override
    public void revealHole() {
        enqueue(() -> {
            if (dealerCards.size() > 1) {
                Card hole = cardAt(true, 1);
                if (hole != null && !dealerCards.get(1).isFaceUp()) {
                    dealerCards.get(1).reveal(hole);
                    playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.1f);
                }
            }
            render();
        }, STEP_TICKS);
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
        }, STEP_TICKS);
    }

    @Override
    public void reset() {
        enqueue(() -> {
            clearCards();
            render();
        }, STEP_TICKS);
    }

    @Override
    public void whenSettled(Runnable action) {
        // Give the player a moment to read the table before anything covers it.
        enqueue(() -> { }, READ_TICKS);
        enqueue(action, 0);
    }

    /** Puts every card in a row in its place, re-centring the row as it grows. */
    private void layoutRow(boolean dealer) {
        List<CardVisual> row = dealer ? dealerCards : playerCards;
        for (int i = 0; i < row.size(); i++) {
            Location slot = layout.cardSlot(i, row.size(), dealer);
            row.get(i).moveTo(slot);
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
