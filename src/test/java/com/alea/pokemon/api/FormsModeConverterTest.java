package com.alea.pokemon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.alea.pokemon.domain.FormsMode;

class FormsModeConverterTest {

    private final FormsModeConverter converter = new FormsModeConverter();

    @Test
    void acceptsTheExactName() {
        assertThat(converter.convert("ALL")).isEqualTo(FormsMode.ALL);
    }

    @Test
    void isCaseInsensitiveAndIgnoresSurroundingBlanks() {
        assertThat(converter.convert(" best_per_species ")).isEqualTo(FormsMode.BEST_PER_SPECIES);
        assertThat(converter.convert("default_only")).isEqualTo(FormsMode.DEFAULT_ONLY);
    }

    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> converter.convert("everything")).isInstanceOf(IllegalArgumentException.class);
    }
}
