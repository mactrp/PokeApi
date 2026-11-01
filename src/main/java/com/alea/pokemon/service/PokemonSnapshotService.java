package com.alea.pokemon.service;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alea.pokemon.domain.PokemonSnapshot;
import com.alea.pokemon.loader.PokemonLoader;

/**
 * Owns the snapshot the rankings are computed on. A new snapshot replaces the previous one
 * with a single atomic swap, and only after it has been loaded completely: readers never see
 * a half-built catalogue, and a failed refresh leaves the previous snapshot in place.
 */
@Service
public class PokemonSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PokemonSnapshotService.class);

    private final PokemonLoader loader;
    // Lock-free holder: readers see either the old or the new snapshot, never a partial one.
    private final AtomicReference<PokemonSnapshot> current = new AtomicReference<>();

    public PokemonSnapshotService(PokemonLoader loader) {
        this.loader = loader;
    }

    /** Loads a fresh catalogue and publishes it. Throws if the load fails; nothing is published then. */
    public void refresh() {
        PokemonSnapshot snapshot = loader.load();
        // Atomic swap, done only after the load succeeded.
        current.set(snapshot);
        log.info("Published snapshot with {} Pokémon", snapshot.pokemon().size());
    }

    /** Returns the published snapshot, or throws {@link SnapshotNotReadyException} if none exists yet. */
    public PokemonSnapshot current() {
        PokemonSnapshot snapshot = current.get();
        if (snapshot == null) {
            throw new SnapshotNotReadyException();
        }
        return snapshot;
    }
}
