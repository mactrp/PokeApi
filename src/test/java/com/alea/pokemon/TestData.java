package com.alea.pokemon;

import com.alea.pokemon.domain.Pokemon;

public final class TestData {

    private TestData() {
    }

    /** Default form with a species named like the entry. */
    public static Pokemon pokemon(int id, String name, int height, int weight, Integer baseExperience) {
        return new Pokemon(id, name, name, height, weight, baseExperience, true);
    }

    /** Alternative form of {@code species}. */
    public static Pokemon form(int id, String name, String species, int height, int weight, Integer baseExperience) {
        return new Pokemon(id, name, species, height, weight, baseExperience, false);
    }
}
