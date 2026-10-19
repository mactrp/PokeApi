package com.alea.pokemon.api;

import com.alea.pokemon.domain.Pokemon;

/**
 * Public representation of a ranked entry. PokéAPI's units (decimetres, hectograms) are
 * converted to metres and kilograms.
 */
public record PokemonResponse(
        int id,
        String name,
        String species,
        boolean defaultForm,
        double heightMeters,
        double weightKilograms,
        Integer baseExperience) {

    static PokemonResponse from(Pokemon pokemon) {
        return new PokemonResponse(
                pokemon.id(),
                pokemon.name(),
                pokemon.species(),
                pokemon.defaultForm(),
                pokemon.height() / 10.0,
                pokemon.weight() / 10.0,
                pokemon.baseExperience());
    }
}
