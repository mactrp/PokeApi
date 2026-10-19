package com.alea.pokemon.service;

/** A ranking was requested before the first snapshot was published. */
public class SnapshotNotReadyException extends RuntimeException {

    public SnapshotNotReadyException() {
        super("Pokémon data has not been loaded yet");
    }
}
