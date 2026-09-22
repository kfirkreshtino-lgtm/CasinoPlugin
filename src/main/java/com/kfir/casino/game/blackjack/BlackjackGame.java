package com.kfir.casino.game.blackjack;

import com.kfir.casino.CasinoConfig;
import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.game.card.Deck;
import com.kfir.casino.util.Tasks;
import com.kfir.casino.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * One blackjack seat: a single player against an automated dealer.
 *
 * <p>Each player gets their own instance with its own shoe, so any number of players can
 * be mid-hand at the same table block without interfering with one another.
 *
 * <p>Rules implemented: hit, stand, double down on the first two cards, natural blackjack
 * bonus, push on equal totals, dealer draws to seventeen with configurable soft-seventeen
 * behaviour. Splitting and insurance are deliberately left out.
 */
public final class BlackjackGame {

    public enum State {
        BETTING,
        PLAYER_TURN,
        DEALER_TURN,
        FINISHED
    }

    private final CasinoPlugin plugin;
    private final UUID playerId;
    private final String stationId;
    private final Deck shoe;

    private final BlackjackHand playerHand = new BlackjackHand();
    private final BlackjackHand dealerHand = new BlackjackHand();

    private State state = State.BETTING;
    private long bet;
    private long lastBet;
    private boolean doubled;
    private Outcome outcome;
    private long payout;

    private BukkitTask dealerTask;
    private BlackjackMenu menu;

    public BlackjackGame(CasinoPlugin plugin, Player player, String stationId) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.stationId = stationId;
        CasinoConfig config = plugin.config();
        this.shoe = new Deck(config.blackjackDecks(), config.blackjackReshuffleAt());
    }

    // ---------------------------------------------------------------- betting

    /** Adds to the pending bet, clamped to the configured maximum and the chip balance. */
    public void addToBet(long amount) {
        if (state != State.BETTING) {
            return;
        }
        long max = Math.min(plugin.config().blackjackMaxBet(), plugin.chipBank().balance(playerId));
        bet = Math.max(0, Math.min(max, bet + amount));
    }

    public void clearBet() {
        if (state == State.BETTING) {
            bet = 0;
        }
    }

    /** Restores the previous bet after a finished hand, if the player can still afford it. */
    public void repeatBet() {
        if (state == State.BETTING && lastBet > 0) {
            bet = Math.min(lastBet, plugin.chipBank().balance(playerId));
        }
    }

    // ------------------------------------------------------------------- deal

    /** Validates the bet, takes the chips and deals the opening four cards. */
    public String deal() {
        if (state != State.BETTING) {
            return "<red>You are already in a hand.</red>";
        }
        CasinoConfig config = plugin.config();
        if (bet < config.blackjackMinBet()) {
            return "<red>The minimum bet is " + Text.chips(config.blackjackMinBet()) + " chips.</red>";
        }
        if (bet > config.blackjackMaxBet()) {
            return "<red>The maximum bet is " + Text.chips(config.blackjackMaxBet()) + " chips.</red>";
        }
        if (!plugin.chipBank().take(playerId, bet)) {
            return "<red>You do not have that many chips.</red>";
        }

        lastBet = bet;
        doubled = false;
        outcome = null;
        payout = 0;
        playerHand.clear();
        dealerHand.clear();
        shoe.reshuffleIfNeeded();

        playerHand.add(shoe.draw());
        dealerHand.add(shoe.draw());
        playerHand.add(shoe.draw());
        dealerHand.add(shoe.draw());

        state = State.PLAYER_TURN;

        if (playerHand.isNatural() || dealerHand.isNatural()) {
            settle();
        }
        refresh();
        return null;
    }

    // ---------------------------------------------------------------- actions

    public void hit() {
        if (state != State.PLAYER_TURN) {
            return;
        }
        playerHand.add(shoe.draw());
        playSound(Sound.ITEM_BOOK_PAGE_TURN);
        if (playerHand.isBust()) {
            settle();
        } else if (playerHand.total() == 21) {
            stand();
            return;
        }
        refresh();
    }

    public void stand() {
        if (state != State.PLAYER_TURN) {
            return;
        }
        state = State.DEALER_TURN;
        refresh();
        startDealerTurn();
    }

    /** Doubles the stake, takes exactly one more card and stands. */
    public String doubleDown() {
        if (state != State.PLAYER_TURN) {
            return "<red>You cannot double right now.</red>";
        }
        if (playerHand.size() != 2 || doubled) {
            return "<red>You can only double on your first two cards.</red>";
        }
        if (!plugin.chipBank().take(playerId, bet)) {
            return "<red>You need " + Text.chips(bet) + " more chips to double.</red>";
        }
        bet *= 2;
        doubled = true;
        playerHand.add(shoe.draw());
        playSound(Sound.ENTITY_PLAYER_LEVELUP);
        if (playerHand.isBust()) {
            settle();
            refresh();
            return null;
        }
        state = State.DEALER_TURN;
        refresh();
        startDealerTurn();
        return null;
    }

    // ------------------------------------------------------------ dealer turn

    private void startDealerTurn() {
        Tasks.cancel(dealerTask);
        int delay = plugin.config().dealDelayTicks();
        dealerTask = Tasks.timer(plugin, delay, delay, () -> {
            if (state != State.DEALER_TURN) {
                Tasks.cancel(dealerTask);
                return;
            }
            if (dealerShouldHit()) {
                dealerHand.add(shoe.draw());
                playSound(Sound.ITEM_BOOK_PAGE_TURN);
                refresh();
            } else {
                Tasks.cancel(dealerTask);
                dealerTask = null;
                settle();
                refresh();
            }
        });
    }

    private boolean dealerShouldHit() {
        int total = dealerHand.total();
        if (total < 17) {
            return true;
        }
        return total == 17 && dealerHand.isSoft() && plugin.config().dealerHitsSoft17();
    }

    // --------------------------------------------------------------- settling

    private void settle() {
        boolean playerNatural = playerHand.isNatural();
        boolean dealerNatural = dealerHand.isNatural();

        if (playerNatural && dealerNatural) {
            outcome = Outcome.PUSH;
        } else if (playerNatural) {
            outcome = Outcome.PLAYER_BLACKJACK;
        } else if (dealerNatural) {
            outcome = Outcome.DEALER_WIN;
        } else if (playerHand.isBust()) {
            outcome = Outcome.PLAYER_BUST;
        } else if (dealerHand.isBust()) {
            outcome = Outcome.DEALER_BUST;
        } else {
            int player = playerHand.total();
            int dealer = dealerHand.total();
            outcome = player > dealer ? Outcome.PLAYER_WIN
                    : player < dealer ? Outcome.DEALER_WIN : Outcome.PUSH;
        }

        payout = switch (outcome) {
            case PLAYER_BLACKJACK -> bet + Math.round(bet * plugin.config().blackjackPayout());
            case PLAYER_WIN, DEALER_BUST -> bet * 2;
            case PUSH -> bet;
            case PLAYER_BUST, DEALER_WIN -> 0L;
        };

        if (payout > 0) {
            plugin.chipBank().give(playerId, payout);
        }

        state = State.FINISHED;
        announce();
    }

    private void announce() {
        Player player = player();
        if (player == null) {
            return;
        }
        long net = payout - bet;
        String netText = net > 0 ? "<green>+" + Text.chips(net) + "</green>"
                : net < 0 ? "<red>" + Text.chips(net) + "</red>"
                : "<yellow>0</yellow>";
        plugin.message(player, outcome.display() + " <gray>You had </gray>" + playerHand.total()
                + "<gray>, the dealer had </gray>" + dealerHand.total()
                + "<gray>. Chips: </gray>" + netText);
        playSound(outcome.isWin() ? Sound.ENTITY_PLAYER_LEVELUP
                : outcome == Outcome.PUSH ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_VILLAGER_NO);
    }

    /** Starts a fresh betting round while keeping the shoe. */
    public void reset() {
        if (state != State.FINISHED) {
            return;
        }
        playerHand.clear();
        dealerHand.clear();
        bet = 0;
        doubled = false;
        outcome = null;
        payout = 0;
        state = State.BETTING;
        repeatBet();
    }

    /**
     * Ends the game early, refunding anything still at stake.
     *
     * <p>Used on quit, on shutdown and by the leave command. Refunding is deliberate:
     * treating a dropped connection as a loss punishes players for their network.
     */
    public long cancelAndRefund() {
        Tasks.cancel(dealerTask);
        dealerTask = null;
        long refund = 0;
        if (state == State.PLAYER_TURN || state == State.DEALER_TURN) {
            refund = bet;
            plugin.chipBank().give(playerId, refund);
        }
        state = State.FINISHED;
        bet = 0;
        return refund;
    }

    // ---------------------------------------------------------------- helpers

    private void refresh() {
        if (menu != null) {
            menu.refresh();
        }
    }

    private void playSound(Sound sound) {
        Player player = player();
        if (player != null) {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    private Player player() {
        return plugin.getServer().getPlayer(playerId);
    }

    public boolean isBusy() {
        return state == State.PLAYER_TURN || state == State.DEALER_TURN;
    }

    public boolean canDouble() {
        return state == State.PLAYER_TURN && playerHand.size() == 2 && !doubled
                && plugin.chipBank().balance(playerId) >= bet;
    }

    public State state() {
        return state;
    }

    public long bet() {
        return bet;
    }

    public long payout() {
        return payout;
    }

    public Outcome outcome() {
        return outcome;
    }

    public BlackjackHand playerHand() {
        return playerHand;
    }

    public BlackjackHand dealerHand() {
        return dealerHand;
    }

    public UUID playerId() {
        return playerId;
    }

    public String stationId() {
        return stationId;
    }

    public void attachMenu(BlackjackMenu menu) {
        this.menu = menu;
    }

    public BlackjackMenu menu() {
        return menu;
    }
}
