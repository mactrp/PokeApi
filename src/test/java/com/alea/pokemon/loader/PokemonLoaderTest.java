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

    private static PokeApiProperties properties(int concurrency) {
        return new PokeApiProperties("http://unused", 2000, concurrency, 3, Duration.ZERO,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static List<String> urls(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "u" + i).toList();
    }

    private static Pokemon pokemonFor(String url) {
        int id = Integer.parseInt(url.substring(1));
        return pokemon(id, "p" + id, 1, id, 1);
    }

    @Test
    void loadsEveryPokemonFromTheCatalogue() {
        PokemonLoader loader = new PokemonLoader(new FakeGateway(() -> urls(5), PokemonLoaderTest::pokemonFor), properties(4));

        PokemonSnapshot snapshot = loader.load();

        assertThat(snapshot.pokemon()).extracting(Pokemon::id).containsExactly(1, 2, 3, 4, 5);
        assertThat(snapshot.loadedAt()).isNotNull();
    }

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

    @Test
    void failsWhenTheCatalogueCannotBeListed() {
        FakeGateway gateway = new FakeGateway(() -> {
            throw new IllegalStateException("down");
        }, PokemonLoaderTest::pokemonFor);

        assertThatThrownBy(() -> new PokemonLoader(gateway, properties(2)).load())
                .isInstanceOf(SnapshotLoadException.class);
    }

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
            Thread.interrupted();
        }
    }
}
