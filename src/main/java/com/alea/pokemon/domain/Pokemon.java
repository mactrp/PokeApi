package com.alea.pokemon.domain;

/**
 * One entry of PokéAPI's {@code /pokemon} resource, which is a battle form and not a species:
 * a species can have several entries (default form, Mega, Gigantamax, regional variants...).
 *
 * @param id             PokéAPI id; alternative forms start at 10001
 * @param name           entry name, e.g. {@code charizard-mega-x}
 * @param species        name of the species the entry belongs to, e.g. {@code charizard}
 * @param height         in decimetres, as returned by the API
 * @param weight         in hectograms, as returned by the API
 * @param baseExperience may be {@code null}: some forms have no value in the API
 * @param defaultForm    {@code true} for the main form of the species
 */
public record Pokemon(
        int id,
        String name,
        String species,
        int height,
        int weight,
        Integer baseExperience,
        boolean defaultForm) {
}
