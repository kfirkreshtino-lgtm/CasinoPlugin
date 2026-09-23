package com.kfir.casino.structure;

import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationType;
import com.kfir.casino.util.Text;
import com.kfir.casino.game.poker.PokerTable;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
 * <p>Poker tables are built as real card tables instead: a smooth oval of felt inside a
 * raised wooden rail on two pedestals, six chairs, a dealer, a card shoe, a carpet and
 * lanterns hanging over it.
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

    /** Poker felt and rail ellipses: half the length and half the depth, in blocks. */
    private static final double FELT_A = 3.1;
    private static final double FELT_B = 1.6;
    private static final double RAIL_A = 3.65;
    private static final double RAIL_B = 2.15;
    /** Width of each display slice the oval is built from. Smaller is smoother. */
    private static final double SLICE = 0.25;
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
            stations.add(pokerTable(world, ox, oy, oz, spot, idPrefix + "poker-" + index++, markers));
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
     * A six seat poker table that looks like a real one.
     *
     * <p>Blocks can only make boxes, so the visible table is built from display entities:
     * the felt and the rail are thin slices following two ellipses, which gives a smooth
     * oval with a raised padded rail around a thin felt top. Two wooden pedestals hold it
     * up, a dealer in a suit stands at the east end beside the card shoe, and an oval
     * carpet lies underneath.
     *
     * <p>Underneath the displays the table is barrier blocks. They are invisible but solid,
     * so players cannot walk through the table, and right-clicking them hits the table.
     * The middle one is the station block.
     *
     * <p>Chairs are two blocks either side of the middle and in the middle of each long
     * side, where {@link com.kfir.casino.game.poker.PokerLayout} seats the players.
     *
     * @param decorations collects every entity spawned, so removing the casino removes them
     */
    private Station pokerTable(World world, int ox, int oy, int oz, int[] spot, String id, List<UUID> decorations) {
        int cx = ox + spot[0];
        int cy = oy + spot[1];
        int cz = oz + spot[2];
        // Everything below is measured from the middle of the station block, at floor level.
        double mx = cx + 0.5;
        double mz = cz + 0.5;

        // Solid, clickable, invisible core covering the oval and its rail.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (inside(dx, dz, RAIL_A + 0.3, RAIL_B + 0.3)) {
                    set(world, cx + dx, cy, cz + dz, Material.BARRIER);
                }
            }
        }

        // Felt and rail, one thin slice at a time along the long side.
        BlockData felt = Material.GREEN_WOOL.createBlockData();
        BlockData rail = Material.STRIPPED_DARK_OAK_WOOD.createBlockData();
        int slices = (int) Math.ceil(2 * RAIL_A / SLICE);
        for (int i = 0; i < slices; i++) {
            double x0 = -RAIL_A + i * SLICE;
            double mid = x0 + SLICE / 2;
            double outer = halfDepth(mid, RAIL_A, RAIL_B);
            double inner = halfDepth(mid, FELT_A, FELT_B);
            if (outer < 0.05) {
                continue;
            }
            if (inner > 0) {
                decorations.add(box(world, felt, mx + x0, cy + 0.88, mz - inner, SLICE, 0.12, 2 * inner));
                decorations.add(box(world, rail, mx + x0, cy + 0.82, mz + inner, SLICE, 0.25, outer - inner));
                decorations.add(box(world, rail, mx + x0, cy + 0.82, mz - outer, SLICE, 0.25, outer - inner));
            } else {
                decorations.add(box(world, rail, mx + x0, cy + 0.82, mz - outer, SLICE, 0.25, 2 * outer));
            }
        }

        // Two pedestals, each a post on a wide foot.
        BlockData post = Material.STRIPPED_DARK_OAK_LOG.createBlockData();
        BlockData foot = Material.DARK_OAK_PLANKS.createBlockData();
        for (int sign = -1; sign <= 1; sign += 2) {
            double px = mx + sign * 1.7;
            decorations.add(box(world, post, px - 0.15, cy, mz - 0.15, 0.3, 0.82, 0.3));
            decorations.add(box(world, foot, px - 0.3, cy, mz - 0.6, 0.6, 0.08, 1.2));
        }

        // The card shoe on the felt in front of the dealer.
        decorations.add(box(world, Material.BLACK_CONCRETE.createBlockData(),
                mx + 2.74, cy + 1.0, mz - 0.15, 0.22, 0.14, 0.3));
        decorations.add(box(world, Material.RED_CONCRETE.createBlockData(),
                mx + 2.76, cy + 1.14, mz - 0.13, 0.18, 0.01, 0.26));

        // Three chairs on each long side, backs away from the table.
        for (int dx = -2; dx <= 2; dx += 2) {
            setData(world, cx + dx, cy, cz + 3, chair(BlockFace.SOUTH));
            setData(world, cx + dx, cy, cz - 3, chair(BlockFace.NORTH));
        }

        // An oval carpet under everything, red with a black border, only where it is empty.
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double ring = (dx * dx) / (5.3 * 5.3) + (dz * dz) / (4.3 * 4.3);
                Block block = world.getBlockAt(cx + dx, cy, cz + dz);
                if (ring <= 1.0 && block.getType() == Material.AIR) {
                    block.setType(ring > 0.72 ? Material.BLACK_CARPET : Material.RED_CARPET, false);
                }
            }
        }

        decorations.add(dealer(world, mx + 3.95, cy + 0.0625, mz));

        Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        setData(world, cx - 1, oy + HEIGHT - 2, cz, lantern);
        setData(world, cx + 1, oy + HEIGHT - 2, cz, lantern);

        Location centre = new Location(world, cx, cy, cz);
        centre.setYaw(0f);
        centre.setPitch(0f);
        return new Station(id, StationType.POKER, centre);
    }

    /** Whether a block offset from the middle lies inside an ellipse. */
    private static boolean inside(double dx, double dz, double a, double b) {
        return (dx * dx) / (a * a) + (dz * dz) / (b * b) <= 1.0;
    }

    /** Half the depth of an ellipse at a point along its long axis, or 0 outside it. */
    private static double halfDepth(double x, double a, double b) {
        double t = 1.0 - (x * x) / (a * a);
        return t <= 0 ? 0 : b * Math.sqrt(t);
    }

    /** A scaled block display filling a box, given its lowest corner and size. */
    private static UUID box(World world, BlockData block, double x, double y, double z,
                            double sizeX, double sizeY, double sizeZ) {
        Location corner = new Location(world, x, y, z);
        BlockDisplay display = world.spawn(corner, BlockDisplay.class, entity -> {
            entity.setBlock(block);
            entity.setPersistent(true);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f((float) sizeX, (float) sizeY, (float) sizeZ),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    /** The dealer: a figure in a black suit at the end of the table, facing the players. */
    private static UUID dealer(World world, double x, double y, double z) {
        Location where = new Location(world, x, y, z, 90f, 0f);
        Mannequin dealer = world.spawn(where, Mannequin.class, entity -> {
            entity.setAI(false);
            entity.setGravity(false);
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.customName(Text.mm("<gold>Dealer</gold>"));
            entity.setCustomNameVisible(true);
            entity.setDescription(Text.mm("<gray>Texas Hold'em</gray>"));
            entity.addScoreboardTag(PokerTable.DEALER_TAG);

            EntityEquipment gear = entity.getEquipment();
            gear.setChestplate(suit(Material.LEATHER_CHESTPLATE));
            gear.setLeggings(suit(Material.LEATHER_LEGGINGS));
            gear.setBoots(suit(Material.LEATHER_BOOTS));
        });
        return dealer.getUniqueId();
    }

    private static ItemStack suit(Material piece) {
        ItemStack item = new ItemStack(piece);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(24, 24, 28));
            item.setItemMeta(meta);
        }
        return item;
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
