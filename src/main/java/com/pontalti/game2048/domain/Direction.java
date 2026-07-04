package com.pontalti.game2048.domain;

/**
 * The four possible directions of a move on the board.
 * <p>
 * It's an enum (not a String or int) because the set of values is closed
 * and known at compile time: this provides type safety, allows exhaustive use
 * in {@code switch} (the compiler charges for all cases) and prevents
 * invalid values from reaching the {@code Board}.
 */
public enum Direction {

    LEFT,
    RIGHT,
    UP,
    DOWN

}
