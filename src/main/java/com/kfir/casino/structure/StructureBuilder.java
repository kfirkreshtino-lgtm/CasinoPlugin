package com.kfir.casino.structure;

import org.bukkit.Location;

/**
 * Places the casino building in the world.
 *
 * <p>Recommendation, and the choice made here: generate the building in code rather than
 * pasting a schematic.
 *
 * <p>A schematic paste needs WorldEdit or FastAsyncWorldEdit as a runtime dependency, a
 * .schem file shipped inside the jar, and a hand-maintained table of where every game
 * table sits inside the paste. Any edit to the build means re-exporting the file and
 * re-measuring those offsets. Procedural generation has none of that: the builder knows
 * exactly where it put each table, so it registers the stations itself and the plugin has
 * zero external dependencies beyond Vault.
 *
 * <p>The interface stays here so a schematic implementation can be dropped in later
 * without any game code changing.
 */
public interface StructureBuilder {

    int width();

    int height();

    int depth();

    /**
     * Builds at the given origin, which is the north-west-bottom corner of the footprint.
     *
     * @param origin      corner to build from
     * @param spawnLabels whether to spawn floating text above each station
     * @param idPrefix    prefix for generated station ids
     */
    BuildResult build(Location origin, boolean spawnLabels, String idPrefix);
}
