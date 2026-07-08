package com.pontalti.game2048.adapter.rest;

/** Thrown when a request references a game id that does not exist. */
public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String id) {
        super("No game with id " + id);
    }
}
