package com.kfir.casino.storage;

import java.util.UUID;

/**
 * Persistence for chip balances.
 *
 * <p>Kept behind an interface so a SQLite implementation can replace the YAML one
 * without touching any game code.
 */
public interface ChipStore {

    long get(UUID playerId);

    void set(UUID playerId, long chips);

    /** Writes pending changes off the main thread. Safe to call often. */
    void saveAsync();

    /** Writes pending changes immediately on the calling thread. Used on shutdown. */
    void saveNow();
}
