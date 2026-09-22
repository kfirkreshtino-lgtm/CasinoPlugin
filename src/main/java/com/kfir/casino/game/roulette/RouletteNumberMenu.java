package com.kfir.casino.game.roulette;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.gui.Items;
import com.kfir.casino.gui.Menu;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Straight-up pocket picker.
 *
 * <p>Pocket zero sits on the top row, one through thirty-six fill the four rows below,
 * and the item stack size doubles as the number so the board reads at a glance. On an
 * American wheel the double zero appears beside the single zero.
 */
public final class RouletteNumberMenu extends Menu {

    private static final int FIRST_NUMBER_SLOT = 9;
    private static final int BACK_SLOT = 49;
    private static final int STAKE_SLOT = 45;

    private final RouletteMenu parent;

    public RouletteNumberMenu(CasinoPlugin plugin, Player player, RouletteMenu parent) {
        super(plugin, player);
        this.parent = parent;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_red>Roulette - pick a number</dark_red>");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build() {
        Wheel wheel = parent.round().wheel();

        if (wheel.style() == Wheel.Style.AMERICAN) {
            pocket(3, 0, wheel);
            pocket(5, Wheel.DOUBLE_ZERO, wheel);
        } else {
            pocket(4, 0, wheel);
        }

        for (int number = 1; number <= 36; number++) {
            pocket(FIRST_NUMBER_SLOT + number - 1, number, wheel);
        }

        set(STAKE_SLOT, Items.chips(Math.min(64, parent.stake()),
                "<gold><bold>Stake: <white>" + Text.chips(parent.stake()) + "</white></bold></gold>",
                "<gray>Change it on the main board.</gray>"));

        set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>Back to the board</yellow>"),
                event -> parent.open());

        fillEmpty();
    }

    private void pocket(int slot, int number, Wheel wheel) {
        Material material = wheel.isGreen(number) ? Material.LIME_CONCRETE
                : wheel.isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        int amount = Math.max(1, Math.min(64, number));
        set(slot, Items.of(material, amount,
                        "<" + wheel.color(number) + "><bold>" + wheel.label(number) + "</bold></"
                                + wheel.color(number) + ">",
                        "<gray>Pays <white>35 to 1</white></gray>",
                        "",
                        "<yellow>Click to bet <white>" + Text.chips(parent.stake())
                                + "</white> chips.</yellow>"),
                event -> {
                    parent.placeBet(RouletteBet.straight(number, parent.stake()));
                    refresh();
                });
    }
}
