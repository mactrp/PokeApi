package com.alea.pokemon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.alea.pokemon.domain.FormsMode;

/** Unit tests for the case-insensitive {@code forms} converter. */
class FormsModeConverterTest {

    private final FormsModeConverter converter = new FormsModeConverter();

    /** An exact constant name converts directly. */
    @Test
    void acceptsTheExactName() {
        assertThat(converter.convert("ALL")).isEqualTo(FormsMode.ALL);
    }

    /** Letter case and surrounding spaces are ignored. */
    @Test
    void isCaseInsensitiveAndIgnoresSurroundingBlanks() {
        assertThat(converter.convert(" best_per_species ")).isEqualTo(FormsMode.BEST_PER_SPECIES);
        assertThat(converter.convert("default_only")).isEqualTo(FormsMode.DEFAULT_ONLY);
    }

    /** Unknown text throws IllegalArgumentException, which Spring turns into a 400. */
    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> converter.convert("everything")).isInstanceOf(IllegalArgumentException.class);
    }
}
