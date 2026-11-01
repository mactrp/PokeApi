package com.alea.pokemon.client;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.alea.pokemon.client.PokeApiPayloads.Detail;
import com.alea.pokemon.client.PokeApiPayloads.ListItem;
import com.alea.pokemon.client.PokeApiPayloads.ListResponse;
import com.alea.pokemon.config.PokeApiProperties;
import com.alea.pokemon.domain.Pokemon;

/** Calls PokéAPI with Spring's RestClient and maps the JSON payloads to domain objects. */
@Component
public class RestClientPokeApiGateway implements PokeApiGateway {

    // Client errors that can disappear on retry, so they must not be treated as permanent.
    private static final int REQUEST_TIMEOUT = 408;
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final PokeApiProperties properties;

    public RestClientPokeApiGateway(RestClient restClient, PokeApiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** Fetches the whole catalogue listing in one call and returns the detail URL of every entry. */
    @Override
    public List<String> listPokemonUrls() {
        ListResponse response = call(() -> restClient.get()
                .uri("/pokemon?limit={limit}", properties.listLimit())
                .retrieve()
                .body(ListResponse.class));
        // An empty body means PokéAPI answered with nothing usable.
        if (response == null || response.results() == null) {
            throw new PokeApiException("PokéAPI returned an empty catalogue listing");
        }
        // A catalogue larger than the page size would silently produce wrong rankings.
        if (response.results().size() != response.count()) {
            throw new PokeApiException("Incomplete catalogue: PokéAPI reports %d entries but returned %d (check pokeapi.list-limit)"
                    .formatted(response.count(), response.results().size()));
        }
        return response.results().stream().map(ListItem::url).toList();
    }

    /** Fetches one detail resource and maps it to a {@link Pokemon}. */
    @Override
    public Pokemon fetchPokemon(String url) {
        Detail detail = call(() -> restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(Detail.class));
        if (detail == null || detail.species() == null) {
            throw new PokeApiException("Unexpected PokéAPI response for " + url);
        }
        // Species name comes from the nested species object.
        return new Pokemon(
                detail.id(),
                detail.name(),
                detail.species().name(),
                detail.height(),
                detail.weight(),
                detail.baseExperience(),
                detail.isDefault());
    }

    /**
     * Runs a request and flags client errors that retrying cannot fix. Timeouts (408) and rate limiting (429)
     * are client errors that can go away, so they, like 5xx and network failures, are left as they are.
     */
    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            // Rethrown as-is so the retry logic can try again.
            if (status == REQUEST_TIMEOUT || status == TOO_MANY_REQUESTS) {
                throw e;
            }
            // Any other 4xx will not change on retry, so fail fast.
            throw new PermanentPokeApiException("PokéAPI rejected the request with HTTP " + status, e);
        }
    }
}
