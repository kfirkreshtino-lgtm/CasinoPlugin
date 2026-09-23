package com.kfir.casino.structure;

import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationType;
import com.kfir.casino.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a quartz casino hall in plain blocks.
 *
 * <p>Footprint is 25 by 19 by 7. The walls, floor, ceiling lights and entrance are all
 * placed relative to the origin corner, then each game table is stamped as a three by
 * three top with a coloured centre block. That centre block is the station a player
 * right-clicks, and a floating label names it.
 *
 * <p>Poker tables are built as real card tables instead: an oval of felt inside a padded
 * wooden rail, six chairs around it and lanterns hanging over it.
 */
public final class ProceduralBuilder implements StructureBuilder {

    private static final int WIDTH = 25;
    private static final int HEIGHT = 7;
    private static final int DEPTH = 19;

    /**
     * Seat markers, relative to the origin corner. These are the blocks a player clicks,
     * and they sit at the player edge of each table with the felt laid out in front.
     */
    private static final int[][] BLACKJACK_SEATS = {{3, 1, 6}, {7, 1, 6}, {11, 1, 6}, {15, 1, 6}};
    /** Middle of each poker table's felt, which is also its station block. */
    private static final int[][] POKER_TABLES = {{5, 1, 13}, {14, 1, 13}};
    private static final int[] ROULETTE_SPOT = {20, 1, 6};
    private static final int[] CASHIER_SPOT = {20, 1, 15};

    /** Yaw the seated player looks along, which points across the table at the dealer. */
    private static final float FACING_NORTH = 180f;

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public int depth() {
        return DEPTH;
    }

    @Override
    public BuildResult build(Location origin, boolean spawnLabels, String idPrefix) {
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        shell(world, ox, oy, oz);
        entrance(world, ox, oy, oz);

        List<Station> stations = new ArrayList<>();
        List<UUID> markers = new ArrayList<>();

        int index = 1;
        for (int[] spot : BLACKJACK_SEATS) {
            stations.add(seatedTable(world, ox, oy, oz, spot, FACING_NORTH, StationType.BLACKJACK,
                    Material.GREEN_CONCRETE, idPrefix + "blackjack-" + index++));
        }
        index = 1;
        for (int[] spot : POKER_TABLES) {
            stations.add(pokerTable(world, ox, oy, oz, spot, idPrefix + "poker-" + index++));
        }
        stations.add(rouletteTable(world, ox, oy, oz, idPrefix + "roulette-1"));
        stations.add(table(world, ox, oy, oz, CASHIER_SPOT, StationType.CASHIER, idPrefix + "cashier-1"));

        if (spawnLabels) {
            for (Station station : stations) {
                markers.add(label(station));
            }
        }

        return new BuildResult(stations, markers);
    }

    /** Floor, walls, windows, ceiling and lighting. */
    private void shell(World world, int ox, int oy, int oz) {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                boolean wall = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
                boolean corner = (x == 0 || x == WIDTH - 1) && (z == 0 || z == DEPTH - 1);
                boolean innerRing = x == 1 || x == WIDTH - 2 || z == 1 || z == DEPTH - 2;

                for (int y = 0; y < HEIGHT; y++) {
                    Material material;
                    if (y == 0) {
                        material = wall ? Material.POLISHED_ANDESITE
                                : innerRing ? Material.RED_CONCRETE : Material.POLISHED_ANDESITE;
                    } else if (y == HEIGHT - 1) {
                        boolean lamp = !wall && x % 6 == 3 && z % 6 == 3;
                        material = lamp ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ;
                    } else if (wall) {
                        if (corner) {
                            material = Material.GOLD_BLOCK;
                        } else if (y == 3 && (x + z) % 4 == 0) {
                            material = Material.GLASS;
                        } else {
                            material = Material.QUARTZ_BLOCK;
                        }
                    } else {
                        material = Material.AIR;
                    }
                    set(world, ox + x, oy + y, oz + z, material);
                }
            }
        }
    }

    /** A three wide doorway in the south wall with a gold frame. */
    private void entrance(World world, int ox, int oy, int oz) {
        int z = DEPTH - 1;
        for (int x = 11; x <= 13; x++) {
            for (int y = 1; y <= 3; y++) {
                set(world, ox + x, oy + y, oz + z, Material.AIR);
            }
        }
        for (int y = 1; y <= 4; y++) {
            set(world, ox + 10, oy + y, oz + z, Material.GOLD_BLOCK);
            set(world, ox + 14, oy + y, oz + z, Material.GOLD_BLOCK);
        }
        for (int x = 10; x <= 14; x++) {
            set(world, ox + x, oy + 4, oz + z, Material.GOLD_BLOCK);
        }
    }

    /**
     * A table a player sits at, with felt laid out in front of the seat.
     *
     * <p>The seat marker is at the player edge and the felt runs three blocks away from it
     * towards the dealer, so there is room for the cards, the bet and the dealer hand. The
     * yaw is stored on the station so the card layout knows which way the table faces.
     */
    private Station seatedTable(World world, int ox, int oy, int oz, int[] seatSpot, float yaw,
                                StationType type, Material felt, String id) {
        double radians = Math.toRadians(yaw);
        int forwardX = (int) Math.round(-Math.sin(radians));
        int forwardZ = (int) Math.round(Math.cos(radians));
        int sideX = (int) Math.round(Math.cos(radians));
        int sideZ = (int) Math.round(Math.sin(radians));

        int bx = ox + seatSpot[0];
        int by = oy + seatSpot[1];
        int bz = oz + seatSpot[2];

        for (int forward = 0; forward <= 2; forward++) {
            for (int side = -1; side <= 1; side++) {
                int x = bx + forwardX * forward + sideX * side;
                int z = bz + forwardZ * forward + sideZ * side;
                // The far row is the dealer side, in darker stone so the table reads as a table.
                set(world, x, by, z, forward == 2 ? Material.POLISHED_BLACKSTONE : felt);
            }
        }
        set(world, bx, by, bz, type.marker());

        Location seat = new Location(world, bx, by, bz);
        seat.setYaw(yaw);
        seat.setPitch(0f);
        return new Station(id, type, seat);
    }

    /**
     * A six seat poker table, long side running east to west.
     *
     * <pre>
     *      . s s s s s .      s = rail, a dark oak slab in the top half of the block so it
     *    c s f f f f f s c        sits flush with the felt and overhangs like a real rail
     *      s f f F f f s      f = felt, F = the station block in the middle
     *    c s f f f f f s c    c = chair
     *      . s s s s s .      . = left empty, which rounds the corners off
     * </pre>
     *
     * The chairs sit at two blocks either side of the middle and in the middle of each long
     * side, where {@link com.kfir.casino.game.poker.PokerLayout} seats the players.
     */
    private Station pokerTable(World world, int ox, int oy, int oz, int[] spot, String id) {
        int cx = ox + spot[0];
        int cy = oy + spot[1];
        int cz = oz + spot[2];

        Slab rail = (Slab) Material.DARK_OAK_SLAB.createBlockData();
        rail.setType(Slab.Type.TOP);

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 2;
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 2;
                if (corner) {
                    set(world, cx + dx, cy, cz + dz, Material.AIR);
                } else if (edge) {
                    setData(world, cx + dx, cy, cz + dz, rail);
                } else {
                    set(world, cx + dx, cy, cz + dz, Material.GREEN_WOOL);
                }
            }
        }

        // Three chairs on each long side, backs away from the table.
        for (int dx = -2; dx <= 2; dx += 2) {
            setData(world, cx + dx, cy, cz + 3, chair(BlockFace.SOUTH));
            setData(world, cx + dx, cy, cz - 3, chair(BlockFace.NORTH));
        }

        Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        setData(world, cx - 1, oy + HEIGHT - 2, cz, lantern);
        setData(world, cx + 1, oy + HEIGHT - 2, cz, lantern);

        Location centre = new Location(world, cx, cy, cz);
        centre.setYaw(0f);
        centre.setPitch(0f);
        return new Station(id, StationType.POKER, centre);
    }

    /** A dark oak chair. Stairs face the side their tall back is on. */
    private static BlockData chair(BlockFace back) {
        Stairs stairs = (Stairs) Material.DARK_OAK_STAIRS.createBlockData();
        stairs.setFacing(back);
        stairs.setHalf(Stairs.Half.BOTTOM);
        return stairs;
    }

    /** A three by three table top with the clickable station block in the middle. */
    private Station table(World world, int ox, int oy, int oz, int[] spot, StationType type, String id) {
        int cx = ox + spot[0];
        int cy = oy + spot[1];
        int cz = oz + spot[2];

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(world, cx + dx, cy, cz + dz, Material.POLISHED_BLACKSTONE);
            }
        }
        set(world, cx, cy, cz, type.marker());
        return new Station(id, type, new Location(world, cx, cy, cz));
    }

    /** The roulette table plus a decorative red and black ring around it. */
    private Station rouletteTable(World world, int ox, int oy, int oz, String id) {
        Station station = table(world, ox, oy, oz, ROULETTE_SPOT, StationType.ROULETTE, id);
        int cx = station.location().getBlockX();
        int cy = station.location().getBlockY();
        int cz = station.location().getBlockZ();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
                    continue;
                }
                boolean red = ((dx + dz) & 1) == 0;
                set(world, cx + dx, cy, cz + dz, red ? Material.RED_CONCRETE : Material.BLACK_CONCRETE);
            }
        }
        return station;
    }

    /** Floating name tag above a station. */
    private UUID label(Station station) {
        // Poker puts its cards and pot in the middle of the felt, so its name floats higher.
        double height = station.type() == StationType.POKER ? 2.5 : 1.4;
        Location where = station.location().clone().add(0.5, height, 0.5);
        TextDisplay display = where.getWorld().spawn(where, TextDisplay.class, entity -> {
            entity.text(Text.mm(station.type().color() + "<bold>" + station.label() + "</bold>"));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
        });
        return display.getUniqueId();
    }

    private void setData(World world, int x, int y, int z, BlockData data) {
        world.getBlockAt(x, y, z).setBlockData(data, false);
    }

    private void set(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
