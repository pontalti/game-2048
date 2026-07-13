package com.pontalti.game2048.domain;

import java.util.Objects;
import java.util.Random;

/**
 * Orchestrates a 2048 game: maintains the current board, game status and score,
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
 * <b>Valid move flow</b> (requirement 5): move → if the board changed → add the
 * points earned by that move's merges → generate a new 2/4 tile → reassess the
 * win/loss condition. If the move does not change anything, no tile is generated,
 * no points are awarded, and the status remains {@code PLAYING}.
 * <p>
 * <b>Scoring:</b> the score is accumulated <i>here</i>, not in {@link Board}. The
 * board only computes how many points a move would earn ({@link Board#moveScored});
 * it stays immutable and stateless. This is what allows the AI to simulate
 * thousands of hypothetical moves without ever inflating the player's real score.
 */
public final class Game {

    private final Random random;
    private Board board;
    private GameStatus status;

    /**
     * Running score: the sum of all points earned by merges since the game began.
     * Lives here — and not in {@link Board} — because the board is immutable and is
     * also used by the AI to simulate hypothetical moves; accumulating there would
     * both break immutability and let simulated moves inflate the real score.
     */
    private int score;

    /**
     * Creates a game with the initial board already set up: the configured number
     * of tiles on an empty board. The count is drawn from the range
     * {@link InitialGameTileLimit#MIN}..{@link InitialGameTileLimit#MAX}; when both
     * bounds are equal (the current setup, both 2) every game starts with exactly
     * that many tiles, the classic 2048 opening. Widening the range makes the
     * starting count vary, following the statement's "a random number of 2s".
     *
     * @param random source of randomness (injected for reproducibility)
     */
    public Game(Random random){
        this.random = Objects.requireNonNull(random, "random cannot be null");
        Board initial = Board.empty();
        int tiles = InitialGameTileLimit.MIN.value()
                + random.nextInt(InitialGameTileLimit.MAX.value() - InitialGameTileLimit.MIN.value() + 1);
        for (int i = 0; i < tiles; i++) {
            initial = initial.spawnRandom(random);
        }
        this.board = initial;
        this.status = GameStatus.PLAYING;
        this.score = 0;
    }

    /**
     * Alternative constructor that accepts a ready-made starting board. Useful for
     * testing (setting up a specific scenario, e.g., a board one step away from victory)
     * and for loading positions from the problem statement examples.
     * <p>
     * The score starts at zero: points are only earned by merges played through
     * {@link #play(Direction)}, so a board handed in ready-made carries no history.
     *
     * @param initialBoard starting board
     * @param random randomness source
     */
    public Game(Board initialBoard, Random random){
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.board = Objects.requireNonNull(initialBoard, "board cannot be null");
        /*
         Reassesses the status right away: the provided board may already be
         won or lost.
         */
        this.status = evaluate(this.board);
        this.score = 0;
    }

    /**
     * Applies a move in the given direction and returns the new game status.
     * <p>
     * If the game has already ended ({@code WON} or {@code LOST}), skip the move and
     * only returns the current status — the UI should not be able to "continue" a
     * ended game.
     * <p>
     * If the move does not change the board (invalid move), no tile is generated,
     * no points are awarded, and the status remains {@code PLAYING}; the UI can
     * detect this by comparing the board before/after, if you want to display
     * "invalid move".
     * <p>
     * On a valid move the points earned by that move's merges are added to the
     * running {@link #getScore() score}, a new 2/4 tile is generated, and the status
     * is reassessed.
     *
     * @param dir direction already translated by the UI
     * @return the resulting {@link GameStatus} after the move
     */
    public GameStatus play(Direction dir){
        Objects.requireNonNull(dir, "dir cannot be null");

        /*
        Match ended: nothing more is being processed.
        */
        if (this.status != GameStatus.PLAYING){
            return this.status;
        }

        /*
        One single computation: the resulting board and the points it earned.
        */
        MoveResult result = this.board.moveScored(dir);
        Board moved = result.board();

        /*
        Invalid move: the move didn't change anything. It doesn't generate a tile,
        it doesn't score, it doesn't change the status.
        */
        if (moved.sameGridAs(this.board)){
            return this.status;
        }
        /*
        Valid move: score the merges, generate a new 2/4, and update the state.
        */
        addScore(result.points());
        this.board = moved.spawnRandom(this.random);
        this.status = evaluate(this.board);
        return this.status;
    }

    /**
     * Adds the points earned by a valid move to the running score.
     * <p>
     * Private on purpose: the score may only change as a consequence of a real move
     * played through {@link #play(Direction)}. Exposing this would let any adapter
     * inflate the score at will, breaking the guarantee that it reflects actual play.
     *
     * @param points points earned by the move's merges (never negative)
     */
    private void addScore(int points) {
        this.score += points;
    }

    /**
     * Evaluates the current status of the given board.
     * <p>
     * A winning board takes precedence over a losing board. Therefore, when the
     * winning tile is present, this method returns {@link GameStatus#WON} even if
     * no further moves are available.
     *
     * @param board the board whose status is to be evaluated
     * @return {@link GameStatus#WON} if the board contains the winning tile,
     *         {@link GameStatus#LOST} if no valid moves remain, or
     *         {@link GameStatus#PLAYING} otherwise
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

    /**
     * Returns the current board.
     * <p>
     * The returned {@link Board} is exposed for reading and rendering purposes.
     * The game remains responsible for replacing the current board when a move is
     * applied.
     *
     * @return the current board
     */
    public Board getBoard() {
        return this.board;
    }

    /**
     * Returns the current status of the game.
     *
     * @return the current {@link GameStatus}
     */
    public GameStatus getStatus() {
        return this.status;
    }

    /**
     * Returns the player's current score: the sum of the points earned by every
     * merge made since the game began.
     * <p>
     * Scoring follows the original 2048 rule — each merge awards the value of the
     * resulting tile (merging two 2s into a 4 awards 4 points). Sliding tiles
     * without merging, and invalid moves, award nothing. Asking the AI for a hint
     * does not affect the score, since the advisor only simulates moves on
     * immutable boards.
     *
     * @return the accumulated score, starting at 0
     */
    public int getScore() {
        return this.score;
    }

}