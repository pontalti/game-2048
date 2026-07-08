package com.pontalti.game2048.adapter.ai;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Position;
import com.pontalti.game2048.domain.port.MoveAdvisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Offline move advisor (requirement 6), based on the <b>expectimax</b> algorithm.
 * <p>
 * It analyzes the current board and suggests the best direction to move without
 * using the internet, external services, or credentials.
 * <p>
 * <b>Why use expectimax:</b>
 * <p>
 * In 2048, after the player makes a move, a new tile with the value 2 or 4
 * appears randomly in an empty cell. Therefore, there is no opponent actively
 * trying to make the player lose.
 * <p>
 * Expectimax is suitable for this situation because it considers every possible
 * position where a new tile may appear, together with the probability of
 * spawning:
 * <ul>
 * <li>a tile with value 2, with a 90% probability;</li>
 * <li>a tile with value 4, with a 10% probability.</li>
 * </ul>
 * <p>
 * The analysis alternates between two types of situations:
 * <ul>
 * <li>
 * <b>Player move:</b> the algorithm tests every valid direction and selects 
 * the one with the best estimated result. 
 * </li>
 * <li>
 * <b>New tile spawn:</b> the algorithm considers every empty cell and 
 * calculates the average result based on the probabilities of spawning a 
 * tile with value 2 or 4. 
 * </li>
 * </ul>
 * <p>
 * Since it is not practical to analyze every possible move until the end of the
 * game, the search is limited to a specific depth. When this limit is reached,
 * the {@link #heuristic} method assigns a score to the board to estimate whether
 * the current situation is good or bad.
 * <p>
 * <b>Predictable result:</b>
 * <p>
 * The algorithm does not generate random values during the analysis. It only
 * calculates probabilities. Therefore, for the same board, {@link #suggest}
 * will always return the same suggestion.
 */
public final class ExpectimaxAdvisor implements MoveAdvisor {

    /** Default look-ahead: the immediate move plus a couple of plies. */
    private static final int DEFAULT_DEPTH = 3;

    /*
        Heuristic weights (tunable). Only relative magnitudes matter, since
        expectimax compares scores rather than using their absolute values.
    */
    private static final double W_EMPTY = 2.7; // free space to keep maneuvering
    private static final double W_GRADIENT = 1.0; // keep tiles ordered toward a corner
    private static final double W_SMOOTH = 0.1; // neighbors close in value merge easily

    /**
     * Positional "snake" weights: the biggest tile is rewarded for sitting in
     * the top-left corner, with values decreasing along a boustrophedon path.
     * This single term encourages both the corner strategy and monotonic
     * ordering, so we don't need separate corner/monotonicity terms.
     */
    private static final int[][] SNAKE = {
            {15, 14, 13, 12},
            { 8,  9, 10, 11},
            { 7,  6,  5,  4},
            { 0,  1,  2,  3}
    };

    private final int maxDepth;

    /** Creates an advisor with the default search depth. */
    public ExpectimaxAdvisor(){
        this(DEFAULT_DEPTH);
    }

    /**
     * Creates an advisor with a custom search depth (measured in player moves
     * looked ahead). Larger = stronger but slower.
     *
     * @param maxDepth number of player moves to look ahead (must be >= 1)
     */
    public ExpectimaxAdvisor(int maxDepth){
        if (maxDepth < 1){
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        this.maxDepth = maxDepth;
    }

    /**
     * Tries each of the four possible directions and returns the move with the best
     * expected outcome.
     * <p>
     * This method represents the top-level MAX node of the expectimax algorithm.
     * Each direction is simulated and evaluated according to its expected score.
     * <p>
     * Directions that do not change the board are considered invalid moves and are
     * ignored. Therefore, the returned direction always represents a valid move.
     * <p>
     * If none of the directions can change the board, the method returns an empty
     * {@link Optional}, indicating that no valid move is available.
     *
     * @param board the current game board to analyze
     * @return the valid direction with the best expected outcome, or an empty
     * {@link Optional} when no valid move is available 
     */
    @Override
    public Optional<Direction> suggest(Board board) {
        // Build one scoring task per legal direction (illegal moves are skipped).
        List<Direction> legal = new ArrayList<>();
        List<Callable<Double>> tasks = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            Board moved = board.move(dir);
            if (moved.sameGridAs(board)) {
                continue; // illegal move: changes nothing, ignore it
            }
            legal.add(dir);
            tasks.add(() -> expectedValuesAfterMove(moved, maxDepth));
        }

        if (legal.isEmpty()) {
            return Optional.empty(); // dead end
        }

        return getDirection(tasks, legal);
    }

    private Optional<Direction> getDirection(List<Callable<Double>> tasks, List<Direction> legal) {
        // One virtual thread per task; try-with-resources closes the executor.
        List<Future<Double>> futures;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = new ArrayList<>(tasks.size());
            for (Callable<Double> task : tasks) {
                futures.add(pool.submit(task));
            }

            Direction best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < futures.size(); i++) {
                double score = futures.get(i).get(); // waits for this task
                if (score > bestScore) {
                    bestScore = score;
                    best = legal.get(i);
                }
            }
            return Optional.ofNullable(best);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Move search was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Move search failed", e.getCause());
        }
    }

    /**
     * Calculates the expected value of the board after a valid move has been made
     * and a new random tile is about to appear.
     * <p>
     * This method represents a CHANCE node in the expectimax algorithm. It evaluates
     * every possible empty cell where a new tile may be placed.
     * <p>
     * For each empty cell, the method considers both possible tile values:
     * <ul>
     * <li>a tile with value 2, with a 90% probability;</li>
     * <li>a tile with value 4, with a 10% probability.</li>
     * </ul>
     * <p>
     * Each resulting board position is evaluated recursively. The final result is
     * the probability-weighted average of all possible outcomes.
     * <p>
     * The {@code depth} parameter controls how many additional levels of the game
     * tree may still be explored. When the depth limit is reached, the board is
     * evaluated using the heuristic function instead of continuing the search.
     *
     * @param board the board state after the player's move and before a new tile
     * is added 
     * @param depth the remaining search depth
     * @return the probability-weighted expected value of all possible tile spawns
     */
    private double expectedValuesAfterMove(Board board, int depth) {
        List<Position> empties = board.emptyPositions();
        if(empties.isEmpty()){
            return heuristic(board);
        }
        double expected = 0.0;
        double perCell = 1.0 / empties.size();
        for(Position pos : empties){
            expected += perCell * 0.9 * bestMoveValue(board.withTileAt(pos, 2), depth);
            expected += perCell * 0.1 * bestMoveValue(board.withTileAt(pos, 4), depth);
        }
        return expected;
    }

    /**
     * Calculates the value of the best valid move available from the current board
     * position.
     * <p>
     * This method represents a MAX node in the expectimax algorithm, meaning that it
     * is the player's turn to choose a move.
     * <p>
     * The method tests each possible direction and ignores moves that do not change
     * the board. Every valid move is evaluated recursively, and the highest value is
     * returned.
     * <p>
     * The recursive search stops when the remaining depth reaches one (or less) or
     * when the board has no valid moves. In either case, the current board is
     * evaluated using the heuristic function instead of exploring additional
     * positions.
     *
     * @param board the current game board to evaluate
     * @param depth the remaining search depth, measured in player moves (MAX plies)
     *              still to look ahead; this method consumes one ply per level via
     *              its recursive call
     * @return the value of the best valid move, or the heuristic value of the
     *         current board when the depth limit is reached or no valid move exists
     */
    private double bestMoveValue(Board board, int depth) {
        if (depth <= 1 || board.isGameOver()){
            return heuristic(board);
        }
        double best = Double.NEGATIVE_INFINITY;
        boolean anyValid = false;
        for (Direction dir : Direction.values()){
            Board moved = board.move(dir);
            if (moved.sameGridAs(board)){
                continue;
            }
            anyValid = true;
            best = Math.max(best, expectedValuesAfterMove(moved, depth - 1));
        }
        //No legal move from here: treat as a leaf.
        return anyValid ? best : heuristic(board);
    }

    /**
     * Evaluates the current board and returns a score representing how favorable
     * the position is. Higher scores indicate better board states.
     * <p>
     * This method is used when the expectimax search reaches its depth limit or
     * cannot continue exploring additional moves.
     * <p>
     * The score combines multiple characteristics of the board:
     * <ul>
     * <li>
     * <b>Free space:</b> boards with more empty cells receive a higher score, 
     * because they provide more room for future moves and reduce the risk of 
     * reaching a dead end. 
     * </li>
     * <li>
     * <b>Tile positioning:</b> the method rewards boards whose tiles follow a 
     * snake-like order, helping larger values remain grouped and preferably 
     * positioned near a corner. 
     * </li>
     * <li>
     * <b>Smoothness:</b> neighboring tiles with similar values receive a better 
     * score, because they are easier to combine in future moves. 
     * </li>
     * </ul>
     * <p>
     * Each characteristic has a different weight according to its importance.
     * Only the relative difference between these weights matters, since
     * {@link #suggest} compares the resulting scores and selects the move with the
     * highest value.
     *
     * @param board the current game board to evaluate
     * @return a score representing the quality of the board, where higher values
     * indicate more favorable positions 
     */
    private double heuristic(Board board) {
        Integer[][] grid = board.toArray();
        return W_EMPTY * emptyCount(grid)
                + W_GRADIENT * snakeGradient(grid)
                + W_SMOOTH * smoothness(grid);
    }

    /**
     * Calculates the rank of a tile based on its value.
     * <p>
     * In 2048, tile values are powers of two. The rank represents the exponent
     * required to produce the tile value. For example:
     * <ul>
     * <li>a tile with value 2 has rank 1;</li>
     * <li>a tile with value 4 has rank 2;</li>
     * <li>a tile with value 8 has rank 3;</li>
     * <li>a tile with value 16 has rank 4.</li>
     * </ul>
     * <p>
     * The rank is computed as the number of trailing zero bits of the tile value
     * ({@link Integer#numberOfTrailingZeros(int)}), which equals its base-2
     * logarithm because tile values are always powers of two. An empty cell,
     * represented by {@code null}, has rank 0.
     *
     * @param value the value of the tile, or {@code null} for an empty cell
     * @return the base-2 rank of the tile, or 0 when the cell is empty
     */
    private static int rank(Integer value){
        return value == null ? 0 : Integer.numberOfTrailingZeros(value);
    }

    private static int emptyCount(Integer[][] grid){
        int count = 0;
        for (Integer[] row : grid){
            for (Integer v : row){
                if (v == null){
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Evaluates how well the tiles are positioned according to the predefined
     * snake-shaped gradient.
     * <p>
     * The snake gradient assigns different weights to each board position. Higher
     * weights are usually placed near a preferred corner and then continue across
     * the board in a snake-like order.
     * <p>
     * Each tile contributes to the final score according to its rank and the weight
     * of the position where it is located. Therefore, larger tiles receive a higher
     * score when they are placed in positions with higher snake-gradient weights.
     * <p>
     * This encourages the expectimax algorithm to keep the largest tiles near a
     * corner and to organize the remaining tiles in a descending sequence across
     * the board. This arrangement usually makes future combinations easier and
     * reduces the risk of blocking the board.
     *
     * @param grid the current board grid containing the tile values
     * @return the score representing how well the tiles follow the snake-shaped
     * positional order, where higher values indicate a better arrangement 
     */
    private static int snakeGradient(Integer[][] grid){
        int sum = 0;
        for (int r = 0; r < Board.SIZE; r++){
            for (int c = 0; c < Board.SIZE; c++){
                sum += SNAKE[r][c] * rank(grid[r][c]);
            }
        }
        return sum;
    }

    /**
     * Evaluates how similar the values of neighboring non-empty tiles are.
     * <p>
     * This method measures the board's smoothness by comparing adjacent tiles.
     * Tiles with similar ranks receive a smaller penalty, while tiles with very
     * different ranks receive a larger penalty.
     * <p>
     * A smoother board is generally more favorable because neighboring tiles with
     * similar values are more likely to be combined in future moves.
     * <p>
     * Empty cells are ignored, since they do not contain tiles that can be compared
     * or merged.
     *
     * @param grid the current board grid containing the tile values
     * @return the smoothness score of the board, where values closer to zero
     * indicate a smoother and more favorable arrangement, and more negative 
     * values indicate greater differences between neighboring tiles 
     */
    private static int smoothness(Integer[][] grid){
        int score = 0;
        for (int r = 0; r < Board.SIZE; r++){
            for (int c = 0; c < Board.SIZE; c++){
                if (grid[r][c] == null){
                    continue;
                }
                if (c + 1 < Board.SIZE && grid[r][c + 1] != null){
                    score -= Math.abs(rank(grid[r][c]) - rank(grid[r][c + 1]));
                }
                if (r + 1 < Board.SIZE && grid[r + 1][c] != null){
                    score -= Math.abs(rank(grid[r][c]) - rank(grid[r + 1][c]));
                }
            }
        }
        return score;
    }

}
