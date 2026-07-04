package com.pontalti.game2048.domain;

import java.util.Objects;
import java.util.Random;

/**
 * Orchestrates a 2048 game: maintains the current board and game status,
 * applies moves, and determines when a new tile should be generated and when
 * the game ends.
 * <p>
 * <b>Isolation:</b> this class is the core of the domain and has no knowledge
 * of the UI. It does not read keyboard input, print anything, or know whether
 * it is being controlled by a console loop, a Swing {@code KeyListener}, or
 * JavaFX. Its only entry point is {@link #play(Direction)}, which receives a
 * {@link Direction} already translated by the UI adapter. This makes it
 * possible to replace the interface later without changing any game rules.
 * <p>
 * <b>Injected randomness:</b> the {@link Random} instance is provided through
 * the constructor so that tests can use a fixed seed and produce fully
 * reproducible games.
 * <p>
 * <b>Valid move flow</b> (requirement 5): move → if the board changed →
 * generate a new 2/4 tile → reassess the win/loss condition. If the move does
 * not change anything, no tile is generated and the status remains
 * {@code PLAYING}.
 */
public final class Game {

    private final Random random;
    private Board board;
    private GameStatus status;

    /**
     * Creates a game with the initial board already set up: two randomly generated tiles
     * on an empty board (usual 2048 convention).
     *
     * @param random source of randomness (injected for reproducibility)
     */
    public Game(Random random){
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.board = Board.empty()
                            .spawnRandom(random)
                            .spawnRandom(random);
        this.status = GameStatus.PLAYING;
    }

    /**
     * Alternative constructor that accepts a ready-made starting board. Useful for
     * testing (setting up a specific scenario, e.g., a board one step away from victory)
     * and for loading positions from the problem statement examples.
     *
     * @param initialBoard starting board
     * @param random randomness source
     */
    public Game(Board initialBoard, Random random){
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.board = Objects.requireNonNull(initialBoard, "board cannot be null");
        /*
         * Reassesses the status right away: the provided board may already be
         * won or lost.
         */
        this.status = evaluate(this.board);
    }

    /**

     * Applies a move in the given direction and returns the new game status.
     * <p>
     * If the game has already ended ({@code WON} or {@code LOST}), skip the move and
     * only returns the current status — the UI should not be able to "continue" a
     * ended game.
     * <p>
     * If the move does not change the board (invalid move), no tile is
     * generated and the status remains {@code PLAYING}; the UI can detect this
     * by comparing the board before/after, if you want to display "invalid move".
     *
     * @param dir direction already translated by the UI
     * @return the resulting {@link GameStatus} after the move
     */
    public GameStatus play(Direction dir){
        Objects.requireNonNull(dir, "dir cannot be null");

        // Match ended: nothing more is being processed.
        if (this.status != GameStatus.PLAYING){
            return this.status;
        }

        Board moved = this.board.move(dir);

        //Invalid move: the move didn't change anything. It doesn't generate a tile, it doesn't change the status.
        if (moved.sameGridAs(this.board)){
            return this.status;
        }

        //Valid move: generates a new 2/4 and updates the state.
        this.board = moved.spawnRandom(this.random);
        this.status = evaluate(this.board);
        return this.status;
    }

    /**
     * Determines the status of a board: victory takes priority over
     * defeat (if 2048 appears, we win, even if there is no more space).
     */
    private static GameStatus evaluate(Board board){
        if(board.hasWon()){
            return GameStatus.WON;
        }
        if(board.isGameOver()){
            return GameStatus.LOST;
        }
        return GameStatus.PLAYING;
    }

    /** Current board (unchangeable — the UI only reads it for rendering). */
    public Board getBoard() {
        return this.board;
    }

    /** Current match status. */
    public GameStatus getStatus() {
        return this.status;
    }

}
