package com.alea.pokemon.domain;

/** Which PokéAPI entries take part in a ranking. */
public enum FormsMode {
    /** Only the main form of each species (default). */
    DEFAULT_ONLY,
    /** Every entry of {@code /pokemon}; several forms of one species may share the top 5. */
    ALL,
    /** Every form is considered, but only the best one of each species is kept. */
    BEST_PER_SPECIES
}
