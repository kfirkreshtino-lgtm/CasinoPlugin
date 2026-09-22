package com.kfir.casino.structure;

import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationType;
import com.kfir.casino.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
 */
public final class ProceduralBuilder implements StructureBuilder {

    private static final int WIDTH = 25;
    private static final int HEIGHT = 7;
    private static final int DEPTH = 19;

    /** Table centres, relative to the origin corner. */
    private static final int[][] BLACKJACK_SPOTS = {{5, 1, 4}, {5, 1, 9}, {5, 1, 14}};
    private static final int[][] POKER_SPOTS = {{19, 1, 4}, {19, 1, 14}};
    private static final int[] ROULETTE_SPOT = {19, 1, 9};
    private static final int[] CASHIER_SPOT = {12, 1, 15};

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
        for (int[] spot : BLACKJACK_SPOTS) {
            stations.add(table(world, ox, oy, oz, spot, StationType.BLACKJACK,
                    idPrefix + "blackjack-" + index++));
        }
        index = 1;
        for (int[] spot : POKER_SPOTS) {
            stations.add(table(world, ox, oy, oz, spot, StationType.POKER,
                    idPrefix + "poker-" + index++));
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
        Location where = station.location().clone().add(0.5, 1.4, 0.5);
        TextDisplay display = where.getWorld().spawn(where, TextDisplay.class, entity -> {
            entity.text(Text.mm(station.type().color() + "<bold>" + station.label() + "</bold>"));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
        });
        return display.getUniqueId();
    }

    private void set(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
