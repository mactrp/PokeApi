package com.alea.pokemon.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON shapes of the PokéAPI responses. Only the fields the application needs are mapped. */
public final class PokeApiPayloads {

    /** Prevents instantiation: this class only groups nested records. */
    private PokeApiPayloads() {
    }

    /** {@code GET /pokemon?limit=N} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListResponse(int count, List<ListItem> results) {
    }

    /** One catalogue entry: its name and the URL of its detail resource. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListItem(String name, String url) {
    }

    /** {@code GET /pokemon/{id}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            int id,
            String name,
            int height,
            int weight,
            // JSON snake_case names are mapped explicitly; baseExperience is boxed because the API may send null.
            @JsonProperty("base_experience") Integer baseExperience,
            @JsonProperty("is_default") boolean isDefault,
            Species species) {
    }

    /** Species an entry belongs to; only its name is needed. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Species(String name) {
    }
}
