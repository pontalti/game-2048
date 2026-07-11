package com.pontalti.game2048.domain;

/**
 * Defines the allowed number of initial tiles when starting a new game.
 */
public enum InitialGameTileLimit {

    /**
     * Starts the game with the minimum number of initial tiles.
     */
    MIN(2),

    /**
     * Starts the game with the maximum number of initial tiles.
     */
    MAX(2);

    private final int value;

    InitialGameTileLimit(int value) {
        this.value = value;
    }

    /**
     * Returns the configured number of initial tiles.
     *
     * @return the number of tiles
     */
    public int value() {
        return value;
    }
}
