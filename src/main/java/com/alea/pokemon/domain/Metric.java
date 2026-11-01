package com.alea.pokemon.domain;

import java.util.function.Function;

/** Attribute a ranking is computed on. Higher values rank first. */
public enum Metric {
    // Each constant pairs a metric with the accessor that reads it from an entry.
    WEIGHT(Pokemon::weight),
    HEIGHT(Pokemon::height),
    BASE_EXPERIENCE(Pokemon::baseExperience);

    // Accessor that reads this metric from an entry; may return null.
    private final Function<Pokemon, Integer> extractor;

    Metric(Function<Pokemon, Integer> extractor) {
        this.extractor = extractor;
    }

    /** Value of this metric for the given entry, or {@code null} when the API does not provide it. */
    public Integer extract(Pokemon pokemon) {
        return extractor.apply(pokemon);
    }
}
