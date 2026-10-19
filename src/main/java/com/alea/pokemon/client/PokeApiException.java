package com.alea.pokemon.client;

/** PokéAPI answered, but with something the application cannot use. */
public class PokeApiException extends RuntimeException {

    public PokeApiException(String message) {
        super(message);
    }

    public PokeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
