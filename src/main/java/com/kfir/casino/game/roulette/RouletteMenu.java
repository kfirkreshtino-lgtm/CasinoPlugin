package com.kfir.casino.game.roulette;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The roulette betting board.
 *
 * <p>Outside bets sit on the board itself. Straight-up numbers live in a second screen
 * because thirty-seven pockets plus controls do not fit in one double chest. Each click
 * places an independent bet at the current stake, so a player can spread one round across
 * as many numbers and colours as they can afford.
 */
public final class RouletteMenu extends Menu {

    private static final int INFO_SLOT = 4;
    private static final int NUMBERS_SLOT = 10;
    private static final int MY_BETS_SLOT = 16;

    private final RouletteRound round;
    private int stakeIndex;

    public RouletteMenu(CasinoPlugin plugin, Player player, RouletteRound round) {
        super(plugin, player);
        this.round = round;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_red>Roulette</dark_red>");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    public void open() {
        round.addViewer(player, this);
        super.open();
    }

    public long stake() {
        List<Long> steps = plugin.config().stakeSteps();
        return steps.get(Math.max(0, Math.min(stakeIndex, steps.size() - 1)));
    }

    public RouletteRound round() {
        return round;
    }

    @Override
    protected void build() {
        buildInfo();
        buildOutsideBets();
        buildStakeControls();
        fillEmpty();
    }

    private void buildInfo() {
        Wheel wheel = round.wheel();
        String status = switch (round.phase()) {
            case IDLE -> "<gray>Waiting for the first bet</gray>";
            case BETTING -> "<yellow>Betting closes in <white>" + round.secondsLeft() + "s</white></yellow>";
            case SPINNING -> "<gold>Wheel is spinning</gold>";
        };
        String last = round.lastResult() < 0 ? "<gray>none yet</gray>" : wheel.colored(round.lastResult());

        set(INFO_SLOT, Items.of(Material.CLOCK, "<gold><bold>Roulette</bold></gold>",
                status,
                "",
                "<gray>Wheel: <white>" + wheel.style().name() + "</white></gray>",
                "<gray>Last result: </gray>" + last,
                "<gray>Limits: <white>" + Text.chips(plugin.config().rouletteMinBet()) + " - "
                        + Text.chips(plugin.config().rouletteMaxBet()) + "</white> chips</gray>",
                "<gray>Players in round: <white>" + round.playerCount() + "</white></gray>"));

        set(NUMBERS_SLOT, Items.of(Material.BLACK_CONCRETE, "<white><bold>Straight up numbers</bold></white>",
                        "<gray>Pays <white>35 to 1</white>.</gray>",
                        "<gray>Click to choose a pocket.</gray>"),
                event -> new RouletteNumberMenu(plugin, player, this).open());

        List<RouletteBet> myBets = round.betsOf(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Wagered this round: <white>"
                + Text.chips(round.wagered(player.getUniqueId())) + "</white> chips</gray>");
        lore.add("");
        if (myBets.isEmpty()) {
            lore.add("<gray>No bets placed yet.</gray>");
        } else {
            for (int i = 0; i < myBets.size() && i < 10; i++) {
                RouletteBet bet = myBets.get(i);
                lore.add("<gray>- " + bet.describe(round.wheel()) + ": <white>"
                        + Text.chips(bet.amount()) + "</white></gray>");
            }
            if (myBets.size() > 10) {
                lore.add("<gray>and " + (myBets.size() - 10) + " more</gray>");
            }
            lore.add("");
            lore.add("<red>Click to clear and refund.</red>");
        }
        set(MY_BETS_SLOT, Items.of(Material.WRITABLE_BOOK, "<gold><bold>Your bets</bold></gold>",
                lore.toArray(new String[0])), event -> {
            long refunded = round.clearBets(player.getUniqueId());
            if (refunded < 0) {
                plugin.message(player, "<red>Too late, the wheel is spinning.</red>");
            } else if (refunded > 0) {
                plugin.message(player, "<yellow>Cleared your bets and refunded <white>"
                        + Text.chips(refunded) + "</white> chips.</yellow>");
            }
            refresh();
        });
    }

    private void buildOutsideBets() {
        outside(19, BetType.RED, Material.RED_WOOL, "<red>");
        outside(20, BetType.BLACK, Material.BLACK_WOOL, "<dark_gray>");
        outside(21, BetType.ODD, Material.WHITE_WOOL, "<white>");
        outside(22, BetType.EVEN, Material.LIGHT_GRAY_WOOL, "<gray>");
        outside(23, BetType.LOW, Material.CYAN_WOOL, "<aqua>");
        outside(24, BetType.HIGH, Material.PURPLE_WOOL, "<light_purple>");

        outside(28, BetType.DOZEN_1, Material.ORANGE_WOOL, "<gold>");
        outside(29, BetType.DOZEN_2, Material.ORANGE_WOOL, "<gold>");
        outside(30, BetType.DOZEN_3, Material.ORANGE_WOOL, "<gold>");

        outside(32, BetType.COLUMN_1, Material.LIME_WOOL, "<green>");
        outside(33, BetType.COLUMN_2, Material.LIME_WOOL, "<green>");
        outside(34, BetType.COLUMN_3, Material.LIME_WOOL, "<green>");
    }

    private void outside(int slot, BetType type, Material material, String color) {
        set(slot, Items.of(material, color + "<bold>" + type.displayName() + "</bold>",
                        "<gray>Pays <white>" + type.ratio() + " to 1</white></gray>",
                        "",
                        "<yellow>Click to bet <white>" + Text.chips(stake()) + "</white> chips.</yellow>"),
                event -> placeBet(RouletteBet.outside(type, stake())));
    }

    /** Called by the number menu so straight-up bets go through the same validation. */
    void placeBet(RouletteBet bet) {
        String error = round.placeBet(player, bet);
        if (error != null) {
            plugin.message(player, error);
        }
        refresh();
    }

    private void buildStakeControls() {
        List<Long> steps = plugin.config().stakeSteps();
        set(48, Items.of(Material.RED_CONCRETE, "<red>Lower stake</red>"), event -> {
            stakeIndex = Math.max(0, stakeIndex - 1);
            refresh();
        });
        set(49, Items.chips(Math.min(64, stake()), "<gold><bold>Stake: <white>"
                        + Text.chips(stake()) + "</white></bold></gold>",
                "<gray>Applies to the next bet you place.</gray>",
                "<gray>Your chips: <white>"
                        + Text.chips(plugin.chipBank().balance(player)) + "</white></gray>"));
        set(50, Items.of(Material.LIME_CONCRETE, "<green>Raise stake</green>"), event -> {
            stakeIndex = Math.min(steps.size() - 1, stakeIndex + 1);
            refresh();
        });
        set(53, Items.of(Material.BARRIER, "<red>Leave table</red>",
                "<gray>Your placed bets stay in the round.</gray>"), event -> player.closeInventory());
    }
}
