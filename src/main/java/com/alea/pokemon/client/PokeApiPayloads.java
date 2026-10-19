package com.alea.pokemon.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON shapes of the PokéAPI responses. Only the fields the application needs are mapped. */
public final class PokeApiPayloads {

    private PokeApiPayloads() {
    }

    /** {@code GET /pokemon?limit=N} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListResponse(int count, List<ListItem> results) {
    }

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
            @JsonProperty("base_experience") Integer baseExperience,
            @JsonProperty("is_default") boolean isDefault,
            Species species) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Species(String name) {
    }
}
