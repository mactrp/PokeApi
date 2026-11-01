package com.alea.pokemon.loader;

import static com.alea.pokemon.TestData.pokemon;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.alea.pokemon.client.PermanentPokeApiException;
import com.alea.pokemon.client.PokeApiGateway;
import com.alea.pokemon.config.PokeApiProperties;
import com.alea.pokemon.domain.Pokemon;
import com.alea.pokemon.domain.PokemonSnapshot;

/** Loader tests with a fake gateway; retries use zero backoff so they run instantly. */
class PokemonLoaderTest {

    /** Gateway whose behaviour is supplied by lambdas. */
    private record FakeGateway(
            java.util.function.Supplier<List<String>> list,
            Function<String, Pokemon> detail) implements PokeApiGateway {

        @Override
        public List<String> listPokemonUrls() {
            return list.get();
        }

        @Override
        public Pokemon fetchPokemon(String url) {
            return detail.apply(url);
        }
    }

    /** Builds properties with 3 attempts and no backoff; only the concurrency varies. */
    private static PokeApiProperties properties(int concurrency) {
        return new PokeApiProperties("http://unused", 2000, concurrency, 3, Duration.ZERO,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    /** Generates fake detail URLs u1..uN. */
    private static List<String> urls(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "u" + i).toList();
    }

    /** Creates an entry whose id is parsed from the URL. */
    private static Pokemon pokemonFor(String url) {
        int id = Integer.parseInt(url.substring(1));
        return pokemon(id, "p" + id, 1, id, 1);
    }

    /** Every listed entry is loaded, in catalogue order. */
    @Test
    void loadsEveryPokemonFromTheCatalogue() {
        PokemonLoader loader = new PokemonLoader(new FakeGateway(() -> urls(5), PokemonLoaderTest::pokemonFor), properties(4));

        PokemonSnapshot snapshot = loader.load();

        assertThat(snapshot.pokemon()).extracting(Pokemon::id).containsExactly(1, 2, 3, 4, 5);
        assertThat(snapshot.loadedAt()).isNotNull();
    }

    /** A request that fails twice still succeeds on its third attempt. */
    @Test
    void retriesTransientFailuresOfASingleRequest() {
        AtomicInteger attemptsForU2 = new AtomicInteger();
        FakeGateway gateway = new FakeGateway(() -> urls(3), url -> {
            if (url.equals("u2") && attemptsForU2.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return pokemonFor(url);
        });

        PokemonSnapshot snapshot = new PokemonLoader(gateway, properties(2)).load();

        assertThat(snapshot.pokemon()).hasSize(3);
        assertThat(attemptsForU2).hasValue(3);
    }

    /** A request that never succeeds fails the whole load, so no partial snapshot is built. */
    @Test
    void failsTheWholeLoadWhenOneRequestKeepsFailing() {
        FakeGateway gateway = new FakeGateway(() -> urls(5), url -> {
            if (url.equals("u3")) {
                throw new IllegalStateException("permanent");
            }
            return pokemonFor(url);
        });

        assertThatThrownBy(() -> new PokemonLoader(gateway, properties(2)).load())
                .isInstanceOf(SnapshotLoadException.class)
                .hasMessageContaining("every Pokémon");
    }

    /** A permanent failure is attempted exactly once. */
    @Test
    void doesNotSpendRetriesOnAPermanentFailure() {
        AtomicInteger attemptsForU2 = new AtomicInteger();
        FakeGateway gateway = new FakeGateway(() -> urls(3), url -> {
            if (url.equals("u2")) {
                attemptsForU2.incrementAndGet();
                throw new PermanentPokeApiException("PokéAPI rejected the request with HTTP 404", null);
            }
            return pokemonFor(url);
        });

        assertThatThrownBy(() -> new PokemonLoader(gateway, properties(2)).load())
                .isInstanceOf(SnapshotLoadException.class)
                .hasMessageContaining("every Pokémon");

        assertThat(attemptsForU2).hasValue(1);
    }

    /** The listing call also fails fast on a permanent error. */
    @Test
    void doesNotRetryAPermanentFailureWhileListingEither() {
        AtomicInteger listAttempts = new AtomicInteger();
        FakeGateway gateway = new FakeGateway(() -> {
            listAttempts.incrementAndGet();
            throw new PermanentPokeApiException("PokéAPI rejected the request with HTTP 404", null);
        }, PokemonLoaderTest::pokemonFor);

        assertThatThrownBy(() -> new PokemonLoader(gateway, properties(2)).load())
                .isInstanceOf(SnapshotLoadException.class)
                .hasMessageContaining("Not retrying");

        assertThat(listAttempts).hasValue(1);
    }

    /** If listing keeps failing, the load fails. */
    @Test
    void failsWhenTheCatalogueCannotBeListed() {
        FakeGateway gateway = new FakeGateway(() -> {
            throw new IllegalStateException("down");
        }, PokemonLoaderTest::pokemonFor);

        assertThatThrownBy(() -> new PokemonLoader(gateway, properties(2)).load())
                .isInstanceOf(SnapshotLoadException.class);
    }

    /** The semaphore caps in-flight requests at the configured limit. */
    @Test
    void neverExceedsTheConfiguredConcurrency() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        FakeGateway gateway = new FakeGateway(() -> urls(30), url -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return pokemonFor(url);
        });

        PokemonSnapshot snapshot = new PokemonLoader(gateway, properties(3)).load();

        assertThat(snapshot.pokemon()).hasSize(30);
        assertThat(maxInFlight.get()).isBetween(2, 3);
    }

    /** An interrupted caller gets an exception and keeps its interrupt flag set. */
    @Test
    void restoresTheInterruptFlagWhenInterruptedWhileWaiting() {
        // Detail requests block until they are interrupted, so the pending futures cannot complete
        // before the (already interrupted) caller waits on them.
        FakeGateway gateway = new FakeGateway(() -> urls(2), url -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return pokemonFor(url);
        });
        PokemonLoader loader = new PokemonLoader(gateway, properties(2));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(loader::load)
                    .isInstanceOf(SnapshotLoadException.class)
                    .hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clear the flag so other tests are not affected.
            Thread.interrupted();
        }
    }
}
