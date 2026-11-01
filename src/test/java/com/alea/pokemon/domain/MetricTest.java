package com.alea.pokemon.domain;

import static com.alea.pokemon.TestData.pokemon;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the value extraction of {@link Metric}. */
class MetricTest {

    // Sample entry with a different value for each metric.
    private final Pokemon bulbasaur = pokemon(1, "bulbasaur", 7, 69, 64);

    /** Each metric reads its own attribute. */
    @Test
    void extractsTheAttributeEachMetricStandsFor() {
        assertThat(Metric.HEIGHT.extract(bulbasaur)).isEqualTo(7);
        assertThat(Metric.WEIGHT.extract(bulbasaur)).isEqualTo(69);
        assertThat(Metric.BASE_EXPERIENCE.extract(bulbasaur)).isEqualTo(64);
    }

    /** A missing base experience is returned as null. */
    @Test
    void missingBaseExperienceIsNull() {
        assertThat(Metric.BASE_EXPERIENCE.extract(pokemon(2, "unknown", 1, 1, null))).isNull();
    }
}
