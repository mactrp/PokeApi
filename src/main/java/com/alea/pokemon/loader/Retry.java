package com.alea.pokemon.loader;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Minimal retry with exponential backoff: the pause doubles after every failed attempt. */
final class Retry {

    private Retry() {
    }

    /**
     * Runs {@code action}, retrying it while it fails with something {@code retryable} accepts.
     * A failure the predicate rejects is not retried: it surfaces at once.
     */
    static <T> T execute(int maxAttempts, Duration initialBackoff, Predicate<RuntimeException> retryable,
                         Supplier<T> action) {
        int attempts = Math.max(1, maxAttempts);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!retryable.test(e)) {
                    throw new SnapshotLoadException("Not retrying: the failure is permanent", e);
                }
                lastFailure = e;
                if (attempt < attempts) {
                    pause(initialBackoff.multipliedBy(1L << (attempt - 1)));
                }
            }
        }
        throw new SnapshotLoadException("Giving up after %d attempts".formatted(attempts), lastFailure);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SnapshotLoadException("Interrupted while waiting to retry", e);
        }
    }
}
