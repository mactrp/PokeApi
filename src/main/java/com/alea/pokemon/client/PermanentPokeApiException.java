package com.alea.pokemon.client;

/**
 * PokéAPI rejected a request in a way that trying again will not fix (for example a 404).
 * The loader fails fast on these instead of spending its retries.
 */
public class PermanentPokeApiException extends PokeApiException {

    public PermanentPokeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
