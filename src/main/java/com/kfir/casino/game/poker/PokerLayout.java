package com.kfir.casino.game.poker;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Where everything sits around a poker table.
 *
 * <p>The station block is the middle of the felt. The table's long side runs along the
 * station's side axis, with three chairs on each long side. Seats are numbered walking
 * round the table, so "the next seat" is always the next player clockwise, which is the
 * order the dealer button and the action move in.
 *
 * <pre>
 *        5     4     3        far side
 *     +-----------------+
 *     |  felt   board   |
 *     +-----------------+
 *        0     1     2        near side
 * </pre>
 */
public final class PokerLayout {

    public static final int SEATS = 6;

    /** Seat position along the long side of the table, in blocks from the middle. */
    private static final double[] SEAT_SIDE = {-2, 0, 2, 2, 0, -2};
    /** Which long side each seat is on: +1 near, -1 far. */
    private static final int[] SEAT_END = {1, 1, 1, -1, -1, -1};

    /** Distance from the middle of the table to the chairs. */
    private static final double CHAIR = 3.0;
    /** Distance from the middle to where a player's cards lie. */
    private static final double CARD_ROW = 1.0;
    /** Distance from the middle to a player's bet, between their cards and the board. */
    private static final double BET_ROW = 0.5;
    /** Half the gap between a player's two hole cards. */
    private static final double HOLE_GAP = 0.17;
    private static final double BOARD_SPACING = 0.36;

    /** Half the table's size in blocks, chairs included, for telling which blocks belong to it. */
    private static final double HALF_LENGTH = 3.5;
    private static final double HALF_DEPTH = 3.5;

    private final World world;
    private final int blockY;
    private final double centreX;
    private final double centreY;
    private final double centreZ;
    private final double forwardX;
    private final double forwardZ;
    private final double sideX;
    private final double sideZ;

    private PokerLayout(Location stationBlock) {
        this.world = stationBlock.getWorld();
        this.blockY = stationBlock.getBlockY();
        this.centreX = stationBlock.getBlockX() + 0.5;
        this.centreY = stationBlock.getBlockY() + 1.0;
        this.centreZ = stationBlock.getBlockZ() + 0.5;
        double radians = Math.toRadians(stationBlock.getYaw());
        // Minecraft yaw zero looks towards positive Z.
        this.forwardX = -Math.sin(radians);
        this.forwardZ = Math.cos(radians);
        this.sideX = Math.cos(radians);
        this.sideZ = Math.sin(radians);
    }

    public static PokerLayout forStation(Location stationBlock) {
        return new PokerLayout(stationBlock);
    }

    private Location at(double side, double forward, double lift) {
        return new Location(world,
                centreX + sideX * side + forwardX * forward,
                centreY + lift,
                centreZ + sideZ * side + forwardZ * forward);
    }

    /** Where a seated player's invisible seat goes, turned to face the middle of the table. */
    public Location chair(int seat) {
        Location chair = at(SEAT_SIDE[seat], SEAT_END[seat] * CHAIR, -0.75);
        double lookX = -SEAT_END[seat] * forwardX;
        double lookZ = -SEAT_END[seat] * forwardZ;
        chair.setYaw((float) Math.toDegrees(Math.atan2(-lookX, lookZ)));
        return chair;
    }

    /** One of a player's two hole cards, standing on the felt in front of them. */
    public Location holeCard(int seat, int index) {
        double offset = index == 0 ? -HOLE_GAP : HOLE_GAP;
        return at(SEAT_SIDE[seat] + offset, SEAT_END[seat] * CARD_ROW, 0.01);
    }

    /** Name, stack and status floating above a player's cards. */
    public Location seatLabel(int seat) {
        return at(SEAT_SIDE[seat], SEAT_END[seat] * CARD_ROW, 0.6);
    }

    /** The chips a player has bet on this street. */
    public Location bet(int seat) {
        return at(SEAT_SIDE[seat], SEAT_END[seat] * BET_ROW, 0.05);
    }

    /** The dealer button, just beside the player who has it. */
    public Location dealerButton(int seat) {
        return at(SEAT_SIDE[seat] + 0.5, SEAT_END[seat] * (BET_ROW + 0.1), 0.05);
    }

    /** One of the five community cards across the middle of the felt. */
    public Location boardCard(int index) {
        return at((index - 2) * BOARD_SPACING, 0, 0.01);
    }

    /** Pot and what is happening, above the board. */
    public Location centreLabel() {
        return at(0, 0, 0.75);
    }

    public Location centre() {
        return at(0, 0, 0);
    }

    /** True for any block of the table: the felt, the rail and the chairs. */
    public boolean covers(Block block) {
        if (!block.getWorld().equals(world) || block.getY() != blockY) {
            return false;
        }
        double dx = block.getX() + 0.5 - centreX;
        double dz = block.getZ() + 0.5 - centreZ;
        double side = dx * sideX + dz * sideZ;
        double forward = dx * forwardX + dz * forwardZ;
        return Math.abs(side) <= HALF_LENGTH && Math.abs(forward) <= HALF_DEPTH;
    }
}
