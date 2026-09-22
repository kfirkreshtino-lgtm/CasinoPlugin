package com.kfir.casino.structure;

import com.kfir.casino.station.Station;

import java.util.List;
import java.util.UUID;

/**
 * What a build produced.
 *
 * @param stations interaction points the builder placed and wants registered
 * @param markers  entity ids of floating labels, so removal can clean them up
 */
public record BuildResult(List<Station> stations, List<UUID> markers) {
}
