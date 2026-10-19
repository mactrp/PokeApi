package com.alea.pokemon.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads the first snapshot at startup. Runners execute before the readiness state switches to
 * {@code ACCEPTING_TRAFFIC}, so the readiness probe stays down until the data is there.
 * If the load fails the application fails to start and the orchestrator restarts it.
 */
@Component
@ConditionalOnProperty(prefix = "pokeapi", name = "preload", havingValue = "true", matchIfMissing = true)
public class SnapshotInitializer implements ApplicationRunner {

    private final PokemonSnapshotService snapshots;

    public SnapshotInitializer(PokemonSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public void run(ApplicationArguments args) {
        snapshots.refresh();
    }
}
