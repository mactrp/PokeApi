package com.alea.pokemon.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the PokéAPI client and of the snapshot loader (prefix {@code pokeapi}).
 *
 * @param baseUrl        root of the API, e.g. {@code https://pokeapi.co/api/v2}
 * @param listLimit      page size used to list the whole catalogue in a single call
 * @param concurrency    maximum number of detail requests in flight at the same time
 * @param maxAttempts    attempts per request before the whole load is considered failed
 * @param initialBackoff pause before the second attempt; doubles on every further attempt
 * @param connectTimeout TCP connect timeout
 * @param readTimeout    response timeout per request
 */
@ConfigurationProperties(prefix = "pokeapi")
public record PokeApiProperties(
        String baseUrl,
        int listLimit,
        int concurrency,
        int maxAttempts,
        Duration initialBackoff,
        Duration connectTimeout,
        Duration readTimeout) {
}
