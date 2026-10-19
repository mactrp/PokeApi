package com.alea.pokemon.loader;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alea.pokemon.client.PermanentPokeApiException;
import com.alea.pokemon.client.PokeApiGateway;
import com.alea.pokemon.config.PokeApiProperties;
import com.alea.pokemon.domain.Pokemon;
import com.alea.pokemon.domain.PokemonSnapshot;

/**
 * Downloads the whole catalogue: one listing call plus one detail call per entry.
 *
 * <p>Detail calls run on virtual threads, bounded by a semaphore so PokéAPI is not flooded.
 * Every call is retried with backoff. If any call still fails, the load fails as a whole:
 * a ranking computed on an incomplete catalogue would be silently wrong.
 */
@Component
public class PokemonLoader {

    private static final Logger log = LoggerFactory.getLogger(PokemonLoader.class);

    private final PokeApiGateway gateway;
    private final PokeApiProperties properties;

    public PokemonLoader(PokeApiGateway gateway, PokeApiProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public PokemonSnapshot load() {
        Instant start = Instant.now();
        List<String> urls = Retry.execute(properties.maxAttempts(), properties.initialBackoff(), PokemonLoader::isTransient, gateway::listPokemonUrls);
        log.info("Loading {} Pokémon from PokéAPI (concurrency {})", urls.size(), properties.concurrency());

        Semaphore permits = new Semaphore(properties.concurrency());
        List<Pokemon> loaded = new ArrayList<>(urls.size());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Pokemon>> futures = new ArrayList<>(urls.size());
            for (String url : urls) {
                futures.add(executor.submit(() -> fetchLimited(url, permits)));
            }
            for (Future<Pokemon> future : futures) {
                loaded.add(future.get());
            }
        } catch (ExecutionException e) {
            executor.shutdownNow();
            throw new SnapshotLoadException("Could not load every Pokémon", e.getCause());
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new SnapshotLoadException("Interrupted while loading Pokémon", e);
        } finally {
            executor.close();
        }

        Instant end = Instant.now();
        log.info("Loaded {} Pokémon in {} ms", loaded.size(), Duration.between(start, end).toMillis());
        return new PokemonSnapshot(loaded, end);
    }

    private Pokemon fetchLimited(String url, Semaphore permits) throws InterruptedException {
        permits.acquire();
        try {
            return Retry.execute(properties.maxAttempts(), properties.initialBackoff(), PokemonLoader::isTransient,
                    () -> gateway.fetchPokemon(url));
        } finally {
            permits.release();
        }
    }

    /** A permanent failure (for example a 404) cannot be fixed by trying again. */
    private static boolean isTransient(RuntimeException failure) {
        return !(failure instanceof PermanentPokeApiException);
    }
}
