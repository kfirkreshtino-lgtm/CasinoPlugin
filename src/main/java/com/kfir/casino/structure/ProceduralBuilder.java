package com.kfir.casino.structure;

import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationType;
import com.kfir.casino.util.Text;
import com.kfir.casino.table.Dealers;
import com.kfir.casino.table.TableLayout;
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
 * <p>The game tables are built to look like real ones. Blocks can only make boxes, so the
 * visible tables are display entities: thin felt inside a raised wooden rail, cut into
 * smooth curves, standing on pedestals over a carpet, each with a dealer in a suit. Under
 * the displays the tables are invisible barrier blocks, which keep them solid and let a
 * click anywhere on them reach the game.
 *
 * <ul>
 *   <li>Blackjack: a half-moon table, the dealer behind the straight edge with a chip tray
 *       and a card shoe, one chair on the curved side.</li>
 *   <li>Poker: an oval for six, with lanterns hanging over it.</li>
 *   <li>Roulette: a long rounded table with a wheel at one end and the betting layout
 *       along the rest, and a croupier beside the wheel.</li>
 *   <li>Cashier: a counter with a marble top and a glass window, a cashier behind it and
 *       a shelf of gold bars and diamonds against the wall.</li>
 * </ul>
 */
public final class ProceduralBuilder implements StructureBuilder {

    private static final int WIDTH = 25;
    private static final int HEIGHT = 7;
    private static final int DEPTH = 19;

    /**
     * Seat markers, relative to the origin corner. These are the blocks a player clicks,
     * and they sit at the player edge of each table with the felt laid out in front.
     */
    private static final int[][] BLACKJACK_SEATS = {{3, 1, 6}, {8, 1, 6}, {13, 1, 6}, {18, 1, 6}};
    /** Middle of each poker table's felt, which is also its station block. */
    private static final int[][] POKER_TABLES = {{5, 1, 13}, {14, 1, 13}};

    /** Poker felt and rail ellipses: half the length and half the depth, in blocks. */
    private static final double FELT_A = 3.1;
    private static final double FELT_B = 1.6;
    private static final double RAIL_A = 3.65;
    private static final double RAIL_B = 2.15;
    /** Width of each display slice the curves are built from. Smaller is smoother. */
    private static final double SLICE = 0.25;

    /** Blackjack half-moon: radius of the felt and of the rail around it. */
    private static final double BLACKJACK_FELT = 2.0;
    private static final double BLACKJACK_RAIL = 2.3;

    /** Roulette: half the length and half the width of its rounded rail. */
    private static final double ROULETTE_LENGTH = 2.3;
    private static final double ROULETTE_WIDTH = 1.5;
    /** Width of the rail inside the roulette table's edge. */
    private static final double ROULETTE_RAIL = 0.3;

    private static final Material[] CHIP_COLORS = {
            Material.WHITE_CONCRETE, Material.RED_CONCRETE, Material.GREEN_CONCRETE,
            Material.BLACK_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE};
    /** Middle of the roulette table, which is also its station block. */
    private static final int[] ROULETTE_SPOT = {21, 1, 10};
    /** Middle of the cashier's counter, which is also its station block. */
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
            stations.add(blackjackTable(world, ox, oy, oz, spot, idPrefix + "blackjack-" + index++, markers));
        }
        index = 1;
        for (int[] spot : POKER_TABLES) {
            stations.add(pokerTable(world, ox, oy, oz, spot, idPrefix + "poker-" + index++, markers));
        }
        stations.add(rouletteTable(world, ox, oy, oz, idPrefix + "roulette-1", markers));
        stations.add(cashier(world, ox, oy, oz, idPrefix + "cashier-1", markers));

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
     * A one-player blackjack table: a half moon with the dealer behind the straight edge.
     *
     * <p>The station block is where the player sits, facing north across the table, and
     * {@link TableLayout} works out the cards from it. The felt is a half circle whose flat
     * side is the dealer's edge, one and a half blocks north of the station, curving round
     * towards the player. The chair is just south of the station.
     */
    private Station blackjackTable(World world, int ox, int oy, int oz, int[] seatSpot, String id,
                                   List<UUID> decorations) {
        int bx = ox + seatSpot[0];
        int by = oy + seatSpot[1];
        int bz = oz + seatSpot[2];
        double mx = bx + 0.5;
        // The dealer's straight edge of the felt. Everything curves south from here.
        double edge = bz - 1.5;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 0; dz++) {
                double depth = bz + dz + 0.5 - edge;
                if (inside(dx, depth, BLACKJACK_RAIL + 0.3, BLACKJACK_RAIL + 0.3)) {
                    set(world, bx + dx, by, bz + dz, Material.BARRIER);
                }
            }
        }

        BlockData felt = Material.GREEN_WOOL.createBlockData();
        BlockData rail = Material.STRIPPED_DARK_OAK_WOOD.createBlockData();
        int slices = (int) Math.ceil(2 * BLACKJACK_RAIL / SLICE);
        for (int i = 0; i < slices; i++) {
            double x0 = -BLACKJACK_RAIL + i * SLICE;
            double mid = x0 + SLICE / 2;
            double outer = halfDepth(mid, BLACKJACK_RAIL, BLACKJACK_RAIL);
            double inner = halfDepth(mid, BLACKJACK_FELT, BLACKJACK_FELT);
            if (outer < 0.05) {
                continue;
            }
            // A straight strip of rail along the dealer's edge.
            decorations.add(box(world, rail, mx + x0, by + 0.82, edge - 0.25, SLICE, 0.25, 0.25));
            if (inner > 0) {
                decorations.add(box(world, felt, mx + x0, by + 0.88, edge, SLICE, 0.12, inner));
                decorations.add(box(world, rail, mx + x0, by + 0.82, edge + inner, SLICE, 0.25, outer - inner));
            } else {
                decorations.add(box(world, rail, mx + x0, by + 0.82, edge, SLICE, 0.25, outer));
            }
        }

        // One pedestal under the middle of the felt.
        decorations.add(box(world, Material.STRIPPED_DARK_OAK_LOG.createBlockData(),
                mx - 0.15, by, edge + 0.75, 0.3, 0.82, 0.3));
        decorations.add(box(world, Material.DARK_OAK_PLANKS.createBlockData(),
                mx - 0.5, by, edge + 0.5, 1.0, 0.08, 0.8));

        // The dealer's chip tray along the straight edge: a wooden tray with a row per colour.
        decorations.add(box(world, Material.DARK_OAK_PLANKS.createBlockData(),
                mx - 0.6, by + 1.0, edge + 0.03, 1.2, 0.04, 0.2));
        for (int k = 0; k < CHIP_COLORS.length; k++) {
            decorations.add(box(world, CHIP_COLORS[k].createBlockData(),
                    mx - 0.57 + k * 0.19, by + 1.04, edge + 0.05, 0.17, 0.05, 0.16));
        }

        // The card shoe at the dealer's side.
        decorations.add(box(world, Material.BLACK_CONCRETE.createBlockData(),
                mx + 1.2, by + 1.0, edge + 0.1, 0.22, 0.14, 0.3));
        decorations.add(box(world, Material.RED_CONCRETE.createBlockData(),
                mx + 1.22, by + 1.14, edge + 0.12, 0.18, 0.01, 0.26));

        setData(world, bx, by, bz + 1, chair(BlockFace.SOUTH));
        carpet(world, by, mx, edge + 1.0, 2.5, 2.7);
        decorations.add(dealer(world, mx, by + 0.0625, edge - 0.55, 0f, "Blackjack"));

        Location seat = new Location(world, bx, by, bz);
        seat.setYaw(FACING_NORTH);
        seat.setPitch(0f);
        return new Station(id, StationType.BLACKJACK, seat);
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

        decorations.add(dealer(world, mx + 3.95, cy + 0.0625, mz, 90f, "Texas Hold'em"));

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

    /**
     * A box turned about its own middle, for the round parts of the roulette wheel.
     *
     * @param across size across the turn, along the local x axis
     * @param along  size along the direction the angle points, the local z axis
     * @param angle  radians about the vertical; zero points south
     */
    private static UUID turnedBox(World world, BlockData block, double x, double y, double z,
                                  double across, double height, double along, double angle) {
        Location middle = new Location(world, x, y, z);
        BlockDisplay display = world.spawn(middle, BlockDisplay.class, entity -> {
            entity.setBlock(block);
            entity.setPersistent(true);
            Quaternionf rotation = new Quaternionf().rotateY((float) angle);
            Vector3f corner = rotation.transform(new Vector3f((float) across / 2f, 0f, (float) along / 2f)).negate();
            entity.setTransformation(new Transformation(
                    corner,
                    rotation,
                    new Vector3f((float) across, (float) height, (float) along),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    /** An oval carpet, red with a black border, laid only where the floor is empty. */
    private static void carpet(World world, int y, double centreX, double centreZ, double halfX, double halfZ) {
        for (int x = (int) Math.floor(centreX - halfX); x <= (int) Math.ceil(centreX + halfX); x++) {
            for (int z = (int) Math.floor(centreZ - halfZ); z <= (int) Math.ceil(centreZ + halfZ); z++) {
                double dx = (x + 0.5 - centreX) / halfX;
                double dz = (z + 0.5 - centreZ) / halfZ;
                double ring = dx * dx + dz * dz;
                Block block = world.getBlockAt(x, y, z);
                if (ring <= 1.0 && block.getType() == Material.AIR) {
                    block.setType(ring > 0.72 ? Material.BLACK_CARPET : Material.RED_CARPET, false);
                }
            }
        }
    }

    /**
     * A dealer: a figure in a black suit that stands still, cannot be hurt and is found
     * again by the games through its tag.
     *
     * @param yaw  direction the dealer faces, towards the players
     * @param game shown under the dealer's name
     */
    private static UUID dealer(World world, double x, double y, double z, float yaw, String game) {
        return staff(world, x, y, z, yaw, "Dealer", game);
    }

    /**
     * A member of staff: a figure in a black suit that stands still and cannot be hurt.
     * Every one carries the dealer tag, which is what protects them.
     */
    private static UUID staff(World world, double x, double y, double z, float yaw, String name, String role,
                              String... extraTags) {
        Location where = new Location(world, x, y, z, yaw, 0f);
        Mannequin dealer = world.spawn(where, Mannequin.class, entity -> {
            entity.setAI(false);
            entity.setGravity(false);
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.customName(Text.mm("<gold>" + name + "</gold>"));
            entity.setCustomNameVisible(true);
            entity.setDescription(Text.mm("<gray>" + role + "</gray>"));
            entity.addScoreboardTag(Dealers.TAG);
            for (String tag : extraTags) {
                entity.addScoreboardTag(tag);
            }

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

    /**
     * The cashier's counter in the south-east corner, facing west into the hall.
     *
     * <p>A wooden counter three blocks long with a white marble top and gold trim, a glass
     * window with an opening in the middle and a money tray in front of it. A cashier in a
     * suit stands behind it, and against the wall is a shelf of gold bars and diamond
     * blocks. Like the game tables, the counter and shelf are barrier blocks under display
     * entities; right-clicking them, or the cashier, opens the cashier menu.
     */
    private Station cashier(World world, int ox, int oy, int oz, String id, List<UUID> decorations) {
        int cx = ox + CASHIER_SPOT[0];
        int cy = oy + CASHIER_SPOT[1];
        int cz = oz + CASHIER_SPOT[2];
        double front = cx;          // west face of the counter
        double north = cz - 1.0;    // the counter runs from here three blocks south
        double length = 3.0;

        for (int dz = -1; dz <= 1; dz++) {
            set(world, cx, cy, cz + dz, Material.BARRIER);
            set(world, cx + 3, cy, cz + dz, Material.BARRIER);
        }

        BlockData wood = Material.DARK_OAK_PLANKS.createBlockData();
        BlockData trim = Material.GOLD_BLOCK.createBlockData();
        BlockData marble = Material.SMOOTH_QUARTZ.createBlockData();
        BlockData glass = Material.GLASS.createBlockData();

        // Counter body, a gold kick plate along the floor and a marble top that overhangs it.
        decorations.add(box(world, wood, front + 0.1, cy, north + 0.05, 0.8, 1.0, length - 0.1));
        decorations.add(box(world, trim, front + 0.08, cy, north + 0.05, 0.03, 0.08, length - 0.1));
        decorations.add(box(world, marble, front - 0.05, cy + 1.0, north, 1.0, 0.08, length));
        decorations.add(box(world, trim, front - 0.07, cy + 1.0, north, 0.03, 0.08, length));

        // Glass window along the counter with an opening in the middle to pass chips through.
        decorations.add(box(world, trim, front + 0.45, cy + 1.08, north + 0.05, 0.08, 0.04, length - 0.1));
        decorations.add(box(world, glass, front + 0.47, cy + 1.12, north + 0.05, 0.04, 0.9, 1.1));
        decorations.add(box(world, glass, front + 0.47, cy + 1.12, north + 1.85, 0.04, 0.9, 1.1));
        decorations.add(box(world, glass, front + 0.47, cy + 1.72, north + 1.15, 0.04, 0.3, 0.7));
        decorations.add(box(world, trim, front + 0.45, cy + 2.02, north + 0.05, 0.08, 0.04, length - 0.1));

        // Money tray under the opening, a pile of diamonds and a few chip stacks on the marble.
        decorations.add(box(world, Material.BLACK_CONCRETE.createBlockData(),
                front + 0.02, cy + 1.08, north + 1.2, 0.38, 0.02, 0.6));
        BlockData diamond = Material.DIAMOND_BLOCK.createBlockData();
        decorations.add(box(world, diamond, front + 0.05, cy + 1.08, north + 0.3, 0.12, 0.12, 0.12));
        decorations.add(box(world, diamond, front + 0.19, cy + 1.08, north + 0.3, 0.12, 0.12, 0.12));
        decorations.add(box(world, diamond, front + 0.12, cy + 1.2, north + 0.3, 0.12, 0.12, 0.12));
        for (int k = 0; k < 4; k++) {
            BlockData chip = CHIP_COLORS[k + 1].createBlockData();
            for (int h = 0; h <= k; h++) {
                decorations.add(box(world, chip, front + 0.08 + k * 0.08, cy + 1.08 + h * 0.022,
                        north + 2.45, 0.07, 0.022, 0.07));
            }
        }

        // The shelf against the east wall: gold bars stacked like a pyramid and diamond blocks.
        double shelf = cx + 3;
        decorations.add(box(world, wood, shelf + 0.2, cy, north + 0.05, 0.8, 1.0, length - 0.1));
        decorations.add(box(world, marble, shelf + 0.15, cy + 1.0, north, 0.85, 0.06, length));
        for (int row = 0; row < 3; row++) {
            for (int bar = 0; bar < 3 - row; bar++) {
                decorations.add(box(world, trim, shelf + 0.45, cy + 1.06 + row * 0.1,
                        north + 0.25 + row * 0.14 + bar * 0.28, 0.24, 0.1, 0.24));
            }
        }
        for (int k = 0; k < 3; k++) {
            decorations.add(box(world, diamond, shelf + 0.45, cy + 1.06, north + 1.9 + k * 0.32, 0.25, 0.25, 0.25));
        }
        decorations.add(box(world, diamond, shelf + 0.45, cy + 1.31, north + 2.06, 0.25, 0.25, 0.25));

        decorations.add(staff(world, cx + 1.9, cy, cz + 0.5, 90f, "Cashier", "Chips for diamonds",
                Dealers.CASHIER_TAG));

        Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        setData(world, cx + 1, oy + HEIGHT - 2, cz, lantern);
        carpet(world, cy, cx - 0.9, cz + 0.5, 1.6, 2.6);

        return new Station(id, StationType.CASHIER, new Location(world, cx, cy, cz));
    }

    /**
     * A roulette table: long and rounded, with the wheel at the north end and the betting
     * layout along the rest. The station block is the middle of the table.
     */
    private Station rouletteTable(World world, int ox, int oy, int oz, String id, List<UUID> decorations) {
        int rx = ox + ROULETTE_SPOT[0];
        int ry = oy + ROULETTE_SPOT[1];
        int rz = oz + ROULETTE_SPOT[2];
        double mx = rx + 0.5;
        double mz = rz + 0.5;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) <= roundedHalfWidth(dz, ROULETTE_LENGTH + 0.3, ROULETTE_WIDTH + 0.3)) {
                    set(world, rx + dx, ry, rz + dz, Material.BARRIER);
                }
            }
        }

        // Felt inside a rail, sliced along the length of the table.
        BlockData felt = Material.GREEN_WOOL.createBlockData();
        BlockData rail = Material.STRIPPED_DARK_OAK_WOOD.createBlockData();
        int slices = (int) Math.ceil(2 * ROULETTE_LENGTH / SLICE);
        for (int i = 0; i < slices; i++) {
            double z0 = -ROULETTE_LENGTH + i * SLICE;
            double mid = z0 + SLICE / 2;
            double outer = roundedHalfWidth(mid, ROULETTE_LENGTH, ROULETTE_WIDTH);
            double inner = roundedHalfWidth(mid, ROULETTE_LENGTH - ROULETTE_RAIL, ROULETTE_WIDTH - ROULETTE_RAIL);
            if (outer < 0.05) {
                continue;
            }
            if (inner > 0) {
                decorations.add(box(world, felt, mx - inner, ry + 0.88, mz + z0, 2 * inner, 0.12, SLICE));
                decorations.add(box(world, rail, mx + inner, ry + 0.82, mz + z0, outer - inner, 0.25, SLICE));
                decorations.add(box(world, rail, mx - outer, ry + 0.82, mz + z0, outer - inner, 0.25, SLICE));
            } else {
                decorations.add(box(world, rail, mx - outer, ry + 0.82, mz + z0, 2 * outer, 0.25, SLICE));
            }
        }

        for (int sign = -1; sign <= 1; sign += 2) {
            double pz = mz + sign * 1.0;
            decorations.add(box(world, Material.STRIPPED_DARK_OAK_LOG.createBlockData(),
                    mx - 0.15, ry, pz - 0.15, 0.3, 0.82, 0.3));
            decorations.add(box(world, Material.DARK_OAK_PLANKS.createBlockData(),
                    mx - 0.6, ry, pz - 0.3, 1.2, 0.08, 0.6));
        }

        double wheelZ = mz - 1.15;
        wheel(world, mx, ry + 1.0, wheelZ, decorations);
        bettingLayout(world, mx, ry + 1.0, mz - 0.35, decorations);

        carpet(world, ry, mx, mz, 2.7, 3.6);
        decorations.add(dealer(world, mx + 1.95, ry + 0.0625, wheelZ, 90f, "Roulette"));

        return new Station(id, StationType.ROULETTE, new Location(world, rx, ry, rz));
    }

    /**
     * Half the width of a rounded table at a point along its length: straight sides with a
     * half circle at each end.
     */
    private static double roundedHalfWidth(double along, double halfLength, double halfWidth) {
        double straight = halfLength - halfWidth;
        double past = Math.abs(along) - straight;
        if (past <= 0) {
            return halfWidth;
        }
        if (past >= halfWidth) {
            return 0;
        }
        return Math.sqrt(halfWidth * halfWidth - past * past);
    }

    /**
     * The roulette wheel lying on the felt: a wooden bowl and rim, 37 pockets in the
     * European order of colours, zero in green and red and black alternating after it, a
     * wooden rotor with a gold spindle, and the ball resting in a pocket.
     */
    private static void wheel(World world, double x, double y, double z, List<UUID> decorations) {
        BlockData wood = Material.DARK_OAK_PLANKS.createBlockData();
        for (int k = 0; k < 3; k++) {
            decorations.add(turnedBox(world, wood, x, y, z, 1.0, 0.03, 1.0, Math.toRadians(30 * k)));
        }

        BlockData rim = Material.STRIPPED_DARK_OAK_WOOD.createBlockData();
        int rimSegments = 28;
        for (int k = 0; k < rimSegments; k++) {
            double angle = 2 * Math.PI * k / rimSegments;
            decorations.add(turnedBox(world, rim, x + 0.56 * Math.sin(angle), y, z + 0.56 * Math.cos(angle),
                    0.14, 0.12, 0.08, angle));
        }

        BlockData green = Material.GREEN_CONCRETE.createBlockData();
        BlockData red = Material.RED_CONCRETE.createBlockData();
        BlockData black = Material.BLACK_CONCRETE.createBlockData();
        for (int k = 0; k < 37; k++) {
            double angle = 2 * Math.PI * k / 37;
            BlockData colour = k == 0 ? green : k % 2 == 1 ? red : black;
            decorations.add(turnedBox(world, colour, x + 0.42 * Math.sin(angle), y + 0.03, z + 0.42 * Math.cos(angle),
                    0.065, 0.035, 0.12, angle));
        }

        decorations.add(turnedBox(world, wood, x, y + 0.03, z, 0.5, 0.05, 0.5, 0));
        decorations.add(turnedBox(world, wood, x, y + 0.03, z, 0.5, 0.05, 0.5, Math.PI / 4));
        decorations.add(turnedBox(world, Material.GOLD_BLOCK.createBlockData(), x, y + 0.08, z, 0.07, 0.14, 0.07, 0));

        double ballAngle = 2 * Math.PI * 7 / 37;
        decorations.add(turnedBox(world, Material.QUARTZ_BLOCK.createBlockData(),
                x + 0.42 * Math.sin(ballAngle), y + 0.065, z + 0.42 * Math.cos(ballAngle), 0.05, 0.05, 0.05, 0));
    }

    /**
     * The betting layout: zero, then the numbers one to thirty-six in three rows of twelve
     * in their real colours, white lines between them, and the six even-money boxes along
     * the side.
     *
     * @param start where the numbers begin along the table; zero sits just before it
     */
    private static void bettingLayout(World world, double x, double y, double start, List<UUID> decorations) {
        double column = 0.16;
        double row = 0.3;
        BlockData white = Material.WHITE_CONCRETE.createBlockData();
        BlockData green = Material.GREEN_CONCRETE.createBlockData();
        BlockData red = Material.RED_CONCRETE.createBlockData();
        BlockData black = Material.BLACK_CONCRETE.createBlockData();

        // White under everything shows through the gaps as the lines of the layout.
        decorations.add(box(world, white, x - 0.47, y, start - 0.22, 0.94, 0.008, 12 * column + 0.24));
        decorations.add(box(world, green, x - 0.45, y + 0.008, start - 0.2, 0.9, 0.01, 0.18));
        for (int c = 0; c < 12; c++) {
            for (int r = 0; r < 3; r++) {
                int number = 3 * c + 3 - r;
                decorations.add(box(world, RED_NUMBERS.contains(number) ? red : black,
                        x - 0.45 + r * row + 0.015, y + 0.008, start + c * column + 0.01, row - 0.03, 0.01, column - 0.02));
            }
        }

        // 1-18, even, red, black, odd, 19-36.
        decorations.add(box(world, white, x + 0.5, y, start - 0.02, 0.32, 0.008, 12 * column + 0.04));
        BlockData[] outside = {green, green, red, black, green, green};
        for (int k = 0; k < outside.length; k++) {
            decorations.add(box(world, outside[k], x + 0.515, y + 0.008, start + k * 2 * column + 0.01,
                    0.29, 0.01, 2 * column - 0.02));
        }
    }

    private static final java.util.Set<Integer> RED_NUMBERS = java.util.Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /** Floating name tag above a station. */
    private UUID label(Station station) {
        // Game tables have cards, chips and a wheel on the felt, so their names float higher.
        Location where = switch (station.type()) {
            case BLACKJACK -> TableLayout.forStation(station.location()).statusLabel().add(0, 0.9, 0);
            case POKER -> station.location().clone().add(0.5, 2.5, 0.5);
            case ROULETTE -> station.location().clone().add(0.5, 2.3, 0.5);
            case CASHIER -> station.location().clone().add(1.5, 2.6, 0.5);
            default -> station.location().clone().add(0.5, 1.4, 0.5);
        };
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
