package com.alea.pokemon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically replaces the snapshot. A failed refresh is logged and the previous data keeps being served. */
@Component
// Active unless pokeapi.preload is set to false.
@ConditionalOnProperty(prefix = "pokeapi", name = "preload", havingValue = "true", matchIfMissing = true)
public class SnapshotRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRefreshJob.class);

    private final PokemonSnapshotService snapshots;

    public SnapshotRefreshJob(PokemonSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    /** Runs once per refresh interval, starting one interval after startup. Failures are logged, not rethrown. */
    @Scheduled(
            initialDelayString = "${pokeapi.refresh-interval:PT24H}",
            fixedDelayString = "${pokeapi.refresh-interval:PT24H}")
    public void refresh() {
        try {
            snapshots.refresh();
        } catch (RuntimeException e) {
            // The old snapshot keeps being served and the next run tries again.
            log.warn("Snapshot refresh failed; keeping the previous snapshot", e);
        }
    }
}
