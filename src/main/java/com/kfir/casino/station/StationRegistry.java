package com.kfir.casino.station;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory index of every registered station, keyed by block position. */
public final class StationRegistry {

    private final Map<String, Station> byKey = new LinkedHashMap<>();

    public void add(Station station) {
        byKey.put(station.key(), station);
    }

    /** Removes the station at this block. Returns the removed station or null. */
    public Station removeAt(Location location) {
        return byKey.remove(Station.key(location));
    }

    public Station at(Location location) {
        return byKey.get(Station.key(location));
    }

    public Station byId(String id) {
        for (Station station : byKey.values()) {
            if (station.id().equalsIgnoreCase(id)) {
                return station;
            }
        }
        return null;
    }

    public Collection<Station> all() {
        return List.copyOf(byKey.values());
    }

    public List<Station> ofType(StationType type) {
        List<Station> result = new ArrayList<>();
        for (Station station : byKey.values()) {
            if (station.type() == type) {
                result.add(station);
            }
        }
        return result;
    }

    /** Next free id for a type, such as blackjack-3. */
    public String nextId(StationType type) {
        String prefix = type.name().toLowerCase();
        int index = 1;
        while (byId(prefix + "-" + index) != null) {
            index++;
        }
        return prefix + "-" + index;
    }

    public int size() {
        return byKey.size();
    }

    public void clear() {
        byKey.clear();
    }
}
