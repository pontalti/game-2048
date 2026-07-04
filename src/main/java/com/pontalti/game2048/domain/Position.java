package com.pontalti.game2048.domain;

/**
 * Immutable coordinate of a board cell (row and column, zero base).
 * <p>
 * Modeled as a {@code record} because it's a classic Value Object: it only carries
 * data, has no identity of its own, and is defined entirely by its
 * values. The record already gives us consistent {@code equals}, {@code hashCode}
 * and {@code toString} for free — essential for using {@code Position}
 * within {@code List} or {@code Set} (e.g., the list of empty cells in the spawn).
 *
 * @param row row index, from 0 (top) to size-1
 * @param col column index, from 0 (left) to size-1
 */
public record Position(int row, int col) {

    /**
     * Validation in the compact constructor: ensures that no {@code Position}
     * is created with a negative coordinate. Failing here, at creation, is better than
     * letting the error propagate and overflow as {@code ArrayIndexOutOfBounds}
     * later on, far from the real cause.
     */
    public Position {
        if (row < 0 || col < 0 ) {
            throw new IllegalArgumentException(
                    "Coordinates cannot be negative: row=" + row + ", col=" + col
            );
        }
    }

}
