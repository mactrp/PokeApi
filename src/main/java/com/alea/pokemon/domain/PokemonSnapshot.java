package com.alea.pokemon.domain;

import java.time.Instant;
import java.util.List;

/** Immutable, complete copy of PokéAPI's catalogue. Rankings are computed on it. */
public record PokemonSnapshot(List<Pokemon> pokemon, Instant loadedAt) {

    public PokemonSnapshot {
        pokemon = List.copyOf(pokemon);
    }
}
