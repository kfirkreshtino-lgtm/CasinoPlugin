package com.kfir.casino.table;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Works out where things sit on a table, given where the player sits and which way the
 * table faces.
 *
 * <p>Everything is expressed in the table frame rather than in world axes, so a table can
 * be built facing any direction and the cards, totals and chips all follow. Forward points
 * from the player towards the dealer; side is the player left-to-right axis.
 */
public final class TableLayout {

    /** Gap between the centres of two neighbouring cards, in blocks. */
    private static final double CARD_SPACING = 0.46;
    /** Widest a row of cards may spread before the cards start to overlap. */
    private static final double MAX_ROW_SPAN = 2.1;
    /** How far the card rows sit from the middle of the table. */
    private static final double PLAYER_ROW = 0.55;
    private static final double DEALER_ROW = 0.60;
    /** Height of the card above the felt, so it does not clip into the block. */
    private static final double CARD_LIFT = 0.06;

    private final World world;
    private final double centreX;
    private final double centreY;
    private final double centreZ;
    private final float yaw;
    private final double forwardX;
    private final double forwardZ;
    private final double sideX;
    private final double sideZ;

    /**
     * Builds the layout for a station block.
     *
     * <p>The station block is the clickable seat marker at the player edge of the felt, and
     * its stored yaw is the direction the player looks. The middle of the table is one block
     * further along that direction, and the playing surface is the top of the block.
     */
    public static TableLayout forStation(Location stationBlock) {
        float yaw = stationBlock.getYaw();
        double radians = Math.toRadians(yaw);
        Location centre = new Location(stationBlock.getWorld(),
                stationBlock.getBlockX() + 0.5 - Math.sin(radians),
                stationBlock.getBlockY() + 1.0,
                stationBlock.getBlockZ() + 0.5 + Math.cos(radians));
        return new TableLayout(centre, yaw);
    }

    /**
     * @param feltCentre middle of the table surface
     * @param yaw        direction the seated player looks, which is towards the dealer
     */
    public TableLayout(Location feltCentre, float yaw) {
        this.world = feltCentre.getWorld();
        this.centreX = feltCentre.getX();
        this.centreY = feltCentre.getY();
        this.centreZ = feltCentre.getZ();
        this.yaw = yaw;

        double radians = Math.toRadians(yaw);
        // Minecraft yaw zero looks towards positive Z.
        this.forwardX = -Math.sin(radians);
        this.forwardZ = Math.cos(radians);
        // The player left-to-right axis is forward turned a quarter turn.
        this.sideX = Math.cos(radians);
        this.sideZ = Math.sin(radians);
    }

    private Location at(double forward, double side, double lift) {
        Location location = new Location(world,
                centreX + forwardX * forward + sideX * side,
                centreY + lift,
                centreZ + forwardZ * forward + sideZ * side);
        location.setYaw(yaw);
        location.setPitch(0f);
        return location;
    }

    /** Where a card is dealt from, just past the dealer on their right. */
    public Location shoe() {
        return at(DEALER_ROW + 0.35, 1.05, CARD_LIFT + 0.02);
    }

    /**
     * Resting place of one card in a hand.
     *
     * @param index    position in the hand, starting at zero
     * @param handSize how many cards the hand holds, so the row stays centred
     * @param dealer   true for the dealer row, false for the player row
     */
    public Location cardSlot(int index, int handSize, boolean dealer) {
        double spacing = handSize > 1 ? Math.min(CARD_SPACING, MAX_ROW_SPAN / (handSize - 1)) : 0;
        double offset = (index - (handSize - 1) / 2.0) * spacing;
        double forward = dealer ? DEALER_ROW : -PLAYER_ROW;
        return at(forward, dealer ? -offset : offset, CARD_LIFT);
    }

    /** Floating total above a hand. */
    public Location handLabel(boolean dealer) {
        return at(dealer ? DEALER_ROW + 0.30 : -PLAYER_ROW - 0.34, 0, 0.30);
    }

    /** The main status line, floating over the middle of the table. */
    public Location statusLabel() {
        return at(0, 0, 0.62);
    }

    /** Where the wagered chips sit, in front of the player. */
    public Location betSpot() {
        return at(-PLAYER_ROW + 0.26, 0, CARD_LIFT);
    }

    /** Where a seated player is placed. */
    public Location seat() {
        return at(-PLAYER_ROW - 0.75, 0, -0.75);
    }

    public float yaw() {
        return yaw;
    }

    public World world() {
        return world;
    }
}
