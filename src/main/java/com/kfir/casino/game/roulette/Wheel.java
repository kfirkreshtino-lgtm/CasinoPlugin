package com.kfir.casino.game.roulette;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The roulette wheel.
 *
 * <p>European is the default: pockets 0 to 36, single zero, house edge 2.70 percent.
 * American adds a double zero, represented internally as pocket 37 and displayed as 00,
 * which doubles the house edge. Switching styles is a config change only.
 */
public final class Wheel {

    /** Internal pocket number used for the American double zero. */
    public static final int DOUBLE_ZERO = 37;

    public enum Style {
        EUROPEAN,
        AMERICAN
    }

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private final Style style;
    private final List<Integer> pockets;

    public Wheel(String styleName) {
        Style parsed;
        try {
            parsed = Style.valueOf(styleName == null ? "EUROPEAN" : styleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            parsed = Style.EUROPEAN;
        }
        this.style = parsed;

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i <= 36; i++) {
            list.add(i);
        }
        if (this.style == Style.AMERICAN) {
            list.add(DOUBLE_ZERO);
        }
        this.pockets = List.copyOf(list);
    }

    public Style style() {
        return style;
    }

    public List<Integer> pockets() {
        return pockets;
    }

    public int spin() {
        return pockets.get(ThreadLocalRandom.current().nextInt(pockets.size()));
    }

    /** A random pocket, used only for the spin animation. */
    public int randomPocket() {
        return spin();
    }

    public boolean isGreen(int pocket) {
        return pocket == 0 || pocket == DOUBLE_ZERO;
    }

    public boolean isRed(int pocket) {
        return RED_NUMBERS.contains(pocket);
    }

    public boolean isBlack(int pocket) {
        return !isGreen(pocket) && !isRed(pocket);
    }

    /** Display label. Pocket 37 shows as 00. */
    public String label(int pocket) {
        return pocket == DOUBLE_ZERO ? "00" : Integer.toString(pocket);
    }

    /** MiniMessage colour name for a pocket. */
    public String color(int pocket) {
        if (isGreen(pocket)) {
            return "green";
        }
        return isRed(pocket) ? "red" : "dark_gray";
    }

    public String colored(int pocket) {
        return "<" + color(pocket) + ">" + label(pocket) + "</" + color(pocket) + ">";
    }
}
