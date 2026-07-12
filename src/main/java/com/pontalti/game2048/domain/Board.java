package com.pontalti.game2048.domain;

import java.util.*;

/**
 * Immutable game board for the 2048 game.
 * <p>
 * Each cell is an {@link Integer}, where {@code null} represents empty space
 * (mirroring the JSON format of the statement, which uses {@code null}). We opted for
 * {@code Integer[][]} instead of {@code int[][]} precisely to be able to use
 * {@code null} as "empty" without needing a sentinel value like 0.
 * <p>
 * <b>Immutability:</b> no operation alters the internal state. Every method
 * that "modifies" the board ({@link #move}, {@link #spawnRandom},
 * {@link #withTileAt}) returns a <i>new</i> instance. This greatly simplifies
 * the AI (expectimax), which needs to simulate moves without corrupting the real state of the
 * game, and eliminates aliasing bugs.
 * <p>
 * <b>Project Key:</b> the four moves are the same algorithm. We only
 * implemented "move left" in one line ({@link #mergeLeft}); the
 * other three are derived by transposition and/or mirroring of the matrix.
 */
public final class Board {

    /** Fixed board dimensions (4x4), as per the examples in the problem statement. */
    public static final int SIZE = 4;

    /** Victory goal. */
    public static final int WINNING_TILE = 2048;

    /**
     * Cell array. It is {@code final} and never directly exposed outside
     * (the getters return copies), guaranteeing the class's true immutability.
     */
    private final Integer[][] cells;

    /**
     * Creates a board from an array. Makes a <b>defensive copy</b>:
     * even if the caller saves a reference to the original array and modifies it
     * then this {@code Board} remains intact.
     *
     * @param cells array {@code SIZE x SIZE}; {@code null} = empty cell
     * @throws IllegalArgumentException if dimensions are not {@code SIZE x SIZE}
     */
    public Board(Integer[][] cells) {
        Objects.requireNonNull(cells, "cells cannot be null");
        if(cells.length != SIZE) {
            throw new IllegalArgumentException("The board should have " + SIZE + "rows.");
        }
        this.cells = new Integer[SIZE][SIZE];
        for(int i = 0; i < SIZE; i++) {
            if(cells[i].length != SIZE) {
                throw new IllegalArgumentException( "Line "+ i +" should have "+ SIZE +" columns.");
            }
            //Integer is immutable, so you just need to copy the references from each row.
            this.cells[i] = Arrays.copyOf(cells[i], SIZE);
        }
    }

    /**
     * Creates a completely empty game board (all cells {@code null}).
     * Useful as a starting point for the initial game setup.
     */
    public static  Board empty() {
        return new Board(new Integer[SIZE][SIZE]);
    }

    /**
     * Applies a move and returns the resulting board — <b>without</b> generating
     * a new tile. The generation of the new 2/4 tile is a separate responsibility
     * ({@link #spawnRandom}), because it should only happen if the move
     * actually changed the board, and that decision rests with whoever orchestrates the game
     * (the {@code Game} class), not the {@code Board}.
     *
     * @param dir direction of the move
     * @return new {@code Board} with the slid and merged cells
     */
    public Board move(Direction dir) {
        Integer[][] result = switch (dir){
            //Left: the base case, applied directly to each line.
            case LEFT -> collapseRows(cells);

            // Right: mirrors each line, collapses to the left, mirrors back.
            case  RIGHT -> mirror(collapseRows(mirror(cells)));

            // Top: transpose (columns become rows), collapse to the left, transpose back.
            case UP -> transpose(collapseRows(transpose(cells)));

            // Bass: transpose, then treat as "right" (mirror/collapse/mirror), transpose back.
            case DOWN -> transpose(mirror(collapseRows(mirror(transpose(cells)))));
        };
        return new Board(result);
    }

    /**
     * Collapses every row of the given matrix toward the left.
     * <p>
     * Each row is processed independently using {@link #mergeLeft(Integer[])},
     * which moves non-empty tiles to the left and merges adjacent tiles according
     * to the game rules.
     * <p>
     * This method does not modify the supplied matrix. It returns a new matrix
     * containing the collapsed rows.
     *
     * @param grid the matrix whose rows are to be collapsed
     * @return a new matrix with every row collapsed toward the left
     */
    private static Integer[][] collapseRows(Integer[][] grid) {
        Integer[][] result = new Integer[SIZE][];
        for(int i = 0; i < SIZE; i++) {
            result[i] = mergeLeft(grid[i]);
        }
        return result;
    }

    /**
     * The core of the logic: merges a single line to the left, in three steps:
     * <ol>
     * <li>compress — removes the {@code null}, joining the values to the left;</li>
     * <li>merge — adds adjacent equal pairs, from left to right,
     * with each tile participating in a maximum of one merge per turn;</li>
     * <li>complete — fills the rest of the line with {@code null} up to {@code SIZE}.</li>
     * </ol>
     * Example: {@code [null, 8, 2, 2]} → compress {@code [8,2,2]} →
     * merge {@code [8,4]} → complete {@code [8,4,null,null]}.
     */
    private static Integer[] mergeLeft(Integer[] row){
        // Step 1: compress (only non-null values, in order).
        List<Integer> values = Arrays.stream(row).filter(Objects::nonNull).toList();

        //Step 2: Merge adjacent matching pairs, from left to right.
        List<Integer> merged = new ArrayList<>();
        for(int i = 0; i < values.size(); i++) {
            if( i + 1 < values.size() && values.get(i).equals(values.get( i + 1)) ){
                merged.add(values.get(i) * 2);
                // skip the second element of the pair: it has already been consumed in the fusion.
                i++;
            } else {
                merged.add(values.get(i));
            }
        }

        // Step 3: Fill with nulls until the line length is reached.
        Integer[] result = new Integer[SIZE];
        for(int i = 0; i < merged.size(); i++) {
            result[i] = merged.get(i);
        }
        return result;
    }

    /**
     * Creates a horizontally mirrored copy of the given square matrix.
     * <p>
     * The order of the columns in each row is reversed without modifying the
     * original matrix. For every cell, the value at {@code grid[r][c]} is copied
     * to {@code result[r][SIZE - 1 - c]}.
     *
     * @param grid the matrix to mirror horizontally
     * @return a new matrix with each row mirrored horizontally
     */
    private static Integer[][] mirror(Integer[][] grid) {
        Integer[][] result = new Integer[SIZE][SIZE];
        for(int r = 0; r < SIZE; r++) {
            for(int c = 0; c < SIZE; c++) {
                result[r][c] = grid[r][SIZE - 1 - c];
            }
        }
        return result;
    }

    /**
     * Creates the transpose of a square matrix.
     * <p>
     * Transposition swaps rows and columns without modifying the original
     * matrix. For every cell, the value at {@code grid[r][c]} is copied to
     * {@code result[c][r]}.
     *
     * @param grid the matrix to transpose
     * @return a new transposed matrix
     */
    private static Integer[][] transpose(Integer[][] grid) {
        Integer[][] result = new Integer[SIZE][SIZE];
        for(int r = 0; r < SIZE; r++) {
            for(int c = 0; c < SIZE; c++) {
                result[c][r] = grid[r][c];
            }
        }
        return result;
    }

    /**
     * Returns a new board with a tile placed in a specific position.
     * Deterministic and public because the AI (expectimax) needs to simulate
     * "what if a 2 appeared here?" without involving randomness.
     *
     * @param pos tile position
     * @param value value to place (typically 2 or 4)
     * @return new {@code Board} with the positioned tile
     */
    public Board withTileAt(Position pos, int value) {
        Integer[][] copy = toArray();
        copy[pos.row()][pos.col()] = value;
        return new Board(copy);
    }

    /**
     * Randomly generates a new tile (90% "2", 10% "4") in any empty cell
     * and returns the resulting board. If there is no empty cell,
     * returns the board itself unchanged.
     * <p>
     * Receives the {@link Random} as a parameter (instead of creating an internal one) so
     * that tests can inject a fixed seed and obtain reproducible results
     * while keeping the {@code Board} deterministic.
     *
     * @param random source of randomness (injected)
     * @return new {@code Board} with one more tile, or {@code this} if it was full
     */
    public Board spawnRandom(Random random) {
        List<Position> empties = emptyPositions();
        if(empties.isEmpty()) {
            return this;
        }
        Position pos = empties.get(random.nextInt(empties.size()));
        int value = random.nextDouble() < 0.9 ? 2 : 4; // 90% 2, 10% 4 (as in the original game)
        return withTileAt(pos, value);
    }

    /**
     * Returns all currently empty positions on the board.
     * <p>
     * A position is considered empty when its corresponding cell contains
     * {@code null}. The board itself is not modified.
     *
     * @return a new list containing the row and column of every empty cell;
     *         an empty list if the board has no available positions
     */
    public List<Position> emptyPositions() {
        List<Position> positions = new ArrayList<>();
        for(int r = 0; r < SIZE; r++) {
            for(int c = 0; c < SIZE; c++) {
                if(cells[r][c] == null) {
                    positions.add(new Position(r, c));
                }
            }
        }
        return positions;
    }

    /**
     * Checks whether the board contains the winning tile.
     * <p>
     * The board is considered won as soon as any cell contains
     * {@link #WINNING_TILE}.
     *
     * @return {@code true} if at least one cell contains the winning tile;
     *         {@code false} otherwise
     */
    public boolean hasWon(){
        return Arrays.stream(cells)
                        .flatMap(Arrays::stream)
                        .anyMatch(value -> value != null && value.equals(WINNING_TILE));
    }

    /**
     * Checks whether the game has no remaining valid moves.
     * <p>
     * The game is over only when the board contains no empty positions and no
     * horizontally or vertically adjacent tiles have the same value and could
     * therefore be merged.
     *
     * @return {@code true} if no valid move is possible;
     *         {@code false} if the board still contains an empty position or at
     *         least one mergeable pair of neighboring tiles
     */
    public boolean isGameOver(){
        if(!emptyPositions().isEmpty()) {
            return false; // there is empty space → you can always move it
        }
        for(int r = 0; r < SIZE; r++) {
            for(int c = 0; c < SIZE; c++) {
                Integer v = cells[r][c];
                // neighbor to the right, same?
                if(c + 1 < SIZE && v.equals(cells[r][c + 1])) {
                    return false;
                }
                // neighbor below the same?
                if (r + 1 < SIZE && v.equals(cells[r + 1][c])){
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Compares the grid content of this board with another board.
     * <p>
     * This method compares only the values stored in the cells and is typically
     * used to determine whether a move changed the board and whether a new tile
     * should be generated.
     *
     * @param other the board whose grid content is to be compared with this board
     * @return {@code true} if both boards contain the same values in the same
     *         positions; {@code false} otherwise
     */
    public boolean sameGridAs(Board other) {
        return Arrays.deepEquals(this.cells, other.cells);
    }

    /**
     * Returns a deep copy of the board's internal grid.
     * <p>
     * Both the outer array and each row are copied, so changes made to the returned
     * matrix do not affect the internal state of this board.
     *
     * @return a new matrix containing the current board values
     */
    public Integer[][] toArray(){
        Integer[][] copy = new Integer[SIZE][];
        for(int r = 0; r < SIZE; r++) {
            copy[r] = Arrays.copyOf(cells[r], SIZE);
        }
        return copy;
    }

    /**
     * Returns the sum of all tile values currently present on the board.
     * <p>
     * Empty cells ({@code null}) are ignored and contribute zero to the total.
     *
     * @return the sum of all non-null tile values
     */
    public int sumOfTiles() {
        int sum = 0;
        for (Integer[] row : this.cells) {
            for (Integer value : row) {
                if (value != null) {
                    sum += value;
                }
            }
        }
        return sum;
    }

    /**
     * Compares this board with another object based on grid content.
     * <p>
     * Two boards are considered equal when they contain the same values in the
     * same positions. This behavior is consistent with {@link #sameGridAs(Board)}.
     *
     * @param o the object to compare with this board
     * @return {@code true} if the given object is a board with identical grid
     *         content; {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Board other)) return false;
        return Arrays.deepEquals(cells, other.cells);
    }

    /**
     * Returns a hash code calculated from the board's grid content.
     * <p>
     * This implementation is consistent with {@link #equals(Object)}.
     *
     * @return the hash code of the board's grid content
     */
    @Override
    public int hashCode() {
        return Arrays.deepHashCode(cells);
    }

    /**
     * Returns a formatted text representation of the board.
     * <p>
     * Each row is written on a separate line. Empty cells are represented by
     * {@code "."}, while occupied cells display their numeric value using a fixed
     * width for alignment. This representation is useful for debugging and for
     * console-based user interfaces.
     *
     * @return a formatted string representing the current board state
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Integer[] row : cells) {
            for (Integer value : row) {
                sb.append(String.format("%5s", value == null ? "." : value));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}
