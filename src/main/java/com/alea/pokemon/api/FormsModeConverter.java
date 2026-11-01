package com.alea.pokemon.api;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.alea.pokemon.domain.FormsMode;

/** Case-insensitive binding of the {@code forms} query parameter ({@code forms=all} works). */
@Component
public class FormsModeConverter implements Converter<String, FormsMode> {

    /** Trims and upper-cases the text before the lookup; an unknown value throws IllegalArgumentException. */
    @Override
    public FormsMode convert(String source) {
        return FormsMode.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
