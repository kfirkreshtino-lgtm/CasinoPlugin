package com.kfir.casino.structure;

import org.bukkit.Location;

import java.util.List;

/**
 * Placeholder for a WorldEdit schematic paste.
 *
 * <p>Not wired up, and deliberately so. Pasting would add WorldEdit or FastAsyncWorldEdit
 * as a runtime dependency and would still need a hand-maintained list of where each table
 * sits inside the paste. If you later want to paste your own build instead, implement this
 * class against the WorldEdit API, then either return the station list from the same
 * offsets table or register the stations by hand with the station add command.
 *
 * <p>Nothing else in the plugin needs to change: the plugin only ever talks to
 * {@link StructureBuilder}.
 */
public final class SchematicBuilder implements StructureBuilder {

    private final String schematicName;

    public SchematicBuilder(String schematicName) {
        this.schematicName = schematicName;
    }

    public String schematicName() {
        return schematicName;
    }

    @Override
    public int width() {
        throw new UnsupportedOperationException("Schematic building is not implemented.");
    }

    @Override
    public int height() {
        throw new UnsupportedOperationException("Schematic building is not implemented.");
    }

    @Override
    public int depth() {
        throw new UnsupportedOperationException("Schematic building is not implemented.");
    }

    @Override
    public BuildResult build(Location origin, boolean spawnLabels, String idPrefix) {
        throw new UnsupportedOperationException(
                "Schematic building is not implemented. Use ProceduralBuilder, or register "
                        + "stations manually inside your own build with /casino station add.");
    }

    /** Stations a schematic implementation would have to report, for reference. */
    public static List<String> requiredStationTypes() {
        return List.of("CASHIER", "BLACKJACK", "ROULETTE", "POKER");
    }
}
