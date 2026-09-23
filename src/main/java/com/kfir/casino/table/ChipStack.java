package com.kfir.casino.table;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A pile of casino chips on the felt, standing for an amount.
 *
 * <p>The amount is broken into chip values the way a dealer would pay it, largest first,
 * and each value gets its own column in its own colour. Every chip is a thin block display.
 * Alternate chips are turned an eighth of a turn, which rounds the square edges off into
 * something that reads as a stack of round chips.
 *
 * <p>Columns are capped in height and number, so a huge pot is a tall pile rather than
 * hundreds of entities.
 */
public final class ChipStack {

    private static final long[] VALUES = {1000, 500, 100, 25, 5, 1};
    private static final Material[] COLORS = {
            Material.ORANGE_CONCRETE,  // 1000
            Material.PURPLE_CONCRETE,  // 500
            Material.BLACK_CONCRETE,   // 100
            Material.GREEN_CONCRETE,   // 25
            Material.RED_CONCRETE,     // 5
            Material.WHITE_CONCRETE};  // 1

    private static final float CHIP_WIDTH = 0.13f;
    private static final float CHIP_HEIGHT = 0.022f;
    private static final double COLUMN_GAP = 0.15;
    private static final int MAX_COLUMNS = 3;
    private static final int MAX_HEIGHT = 10;

    private final Location base;
    private final List<BlockDisplay> chips = new ArrayList<>();
    private long shown = -1;

    /** @param base middle of the pile, on the felt */
    public ChipStack(Location base) {
        this.base = base.clone();
    }

    /** Shows this many chips. Nothing changes if the amount is the same as before. */
    public void set(long amount) {
        if (amount == shown) {
            return;
        }
        shown = amount;
        clear();
        if (amount <= 0) {
            return;
        }

        List<int[]> columns = new ArrayList<>();
        long left = amount;
        for (int i = 0; i < VALUES.length && columns.size() < MAX_COLUMNS; i++) {
            long count = left / VALUES[i];
            left -= count * VALUES[i];
            if (count > 0) {
                columns.add(new int[]{i, (int) Math.min(count, MAX_HEIGHT)});
            }
        }

        for (int c = 0; c < columns.size(); c++) {
            double offset = (c - (columns.size() - 1) / 2.0) * COLUMN_GAP;
            Material color = COLORS[columns.get(c)[0]];
            int height = columns.get(c)[1];
            for (int h = 0; h < height; h++) {
                Location at = base.clone().add(offset, h * CHIP_HEIGHT, 0);
                chips.add(spawnChip(at, color, h % 2 == 1));
            }
        }
    }

    private static BlockDisplay spawnChip(Location at, Material color, boolean turned) {
        return at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(color.createBlockData());
            display.setPersistent(false);
            Quaternionf rotation = new Quaternionf().rotateY(turned ? (float) (Math.PI / 4) : 0f);
            // A block display grows from its corner, so shift it back to turn about its middle.
            Vector3f corner = rotation.transform(new Vector3f(CHIP_WIDTH / 2f, 0f, CHIP_WIDTH / 2f)).negate();
            display.setTransformation(new Transformation(
                    corner,
                    rotation,
                    new Vector3f(CHIP_WIDTH, CHIP_HEIGHT, CHIP_WIDTH),
                    new Quaternionf()));
        });
    }

    private void clear() {
        for (BlockDisplay chip : chips) {
            if (!chip.isDead()) {
                chip.remove();
            }
        }
        chips.clear();
    }

    public void remove() {
        clear();
        shown = -1;
    }
}
