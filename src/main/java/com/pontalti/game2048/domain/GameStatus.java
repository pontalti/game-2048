package com.pontalti.game2048.domain;

/**
 * Current status of a match.
 * <p>
 * Closed enum: a match is always in exactly one of these three states,
 * which provides type safety and allows exhaustive {@code switch} in the UI.
 */
public enum GameStatus {

    /** Game in progress: there are still possible moves and 2048 has not been reached. */
    PLAYING,

    /** Victory: tile 2048 has appeared. The game ends here (design decision). */
    WON,

    /** Defeat: board full and no fusions possible. */
    LOST,
}
