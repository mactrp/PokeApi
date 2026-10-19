package com.alea.pokemon.loader;

/** The catalogue could not be loaded completely. No partial snapshot is ever published. */
public class SnapshotLoadException extends RuntimeException {

    public SnapshotLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
