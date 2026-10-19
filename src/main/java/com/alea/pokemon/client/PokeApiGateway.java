package com.alea.pokemon.client;

import java.util.List;

import com.alea.pokemon.domain.Pokemon;

/** Port to PokéAPI. Implementations throw unchecked exceptions on any failure. */
public interface PokeApiGateway {

    /** URLs of the detail resource of every entry in the catalogue. */
    List<String> listPokemonUrls();

    /** Fetches and maps one detail resource. */
    Pokemon fetchPokemon(String url);
}
