package com.alea.pokemon.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class RetryTest {

    private static final Predicate<RuntimeException> ALWAYS = failure -> true;

    @Test
    void returnsTheResultOfTheFirstSuccessfulAttempt() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.execute(3, Duration.ZERO, ALWAYS, () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesUntilTheActionSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.execute(3, Duration.ZERO, ALWAYS, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttemptsKeepingTheLastCause() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("persistent");

        assertThatThrownBy(() -> Retry.execute(3, Duration.ZERO, ALWAYS, () -> {
            calls.incrementAndGet();
            throw failure;
        }))
                .isInstanceOf(SnapshotLoadException.class)
                .hasMessageContaining("Giving up after 3 attempts")
                .hasCause(failure);

        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryAFailureTheClassifierRejects() {
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException permanent = new IllegalArgumentException("permanent");

        assertThatThrownBy(() -> Retry.execute(3, Duration.ZERO, failure -> !(failure instanceof IllegalArgumentException), () -> {
            calls.incrementAndGet();
            throw permanent;
        }))
                .isInstanceOf(SnapshotLoadException.class)
                .hasMessageContaining("Not retrying")
                .hasCause(permanent);

        assertThat(calls).hasValue(1);
    }

    @Test
    void stillRetriesFailuresTheClassifierAccepts() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.execute(3, Duration.ZERO, failure -> !(failure instanceof IllegalArgumentException), () -> {
            if (calls.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void alwaysTriesAtLeastOnce() {
        assertThat(Retry.execute(0, Duration.ZERO, ALWAYS, () -> "ok")).isEqualTo("ok");
    }

    @Test
    void stopsAndRestoresTheInterruptFlagWhenInterruptedDuringBackoff() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> Retry.execute(2, Duration.ofMillis(1), ALWAYS, () -> {
                throw new IllegalStateException("transient");
            }))
                    .isInstanceOf(SnapshotLoadException.class)
                    .hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
