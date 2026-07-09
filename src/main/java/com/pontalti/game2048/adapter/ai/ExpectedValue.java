package com.pontalti.game2048.adapter.ai;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Position;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Evaluates the expectimax value of a single already-moved board — the subtree
 * rooted at one of the player's legal moves.
 * <p>
 * This class is the unit of parallel work used by {@link ExpectimaxAdvisor}: the
 * advisor creates one {@code ExpectedValue} per legal direction and runs them
 * concurrently. Each instance is self-contained and immutable, and it never
 * shares mutable state with the others (the {@link Board} is immutable), so no
 * synchronization is required.
 * <p>
 * Evaluation alternates between two node types of the expectimax tree:
 * <ul>
 *   <li><b>CHANCE</b> ({@link #call} / {@code expectedValuesAfterMove}) — averages
 *       over every empty cell the outcome of spawning a 2 (90%) or a 4 (10%);</li>
 *   <li><b>MAX</b> ({@code bestMoveValue}) — picks the best of the player's legal
 *       moves.</li>
 * </ul>
 * When the depth limit is reached, {@link #heuristic} scores the board instead of
 * searching deeper. No randomness is used: the expectation is computed
 * analytically, so the result is a pure function of the input board.
 */
public class ExpectedValue implements Callable<Double> {

    private final Board moved;
    private final int maxDepth;

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

    /**
     * Creates a scoring task for one already-moved board.
     *
     * @param moved    the board <b>after</b> a valid player move, before a tile
     *                 spawns; this is the root of the subtree to evaluate
     * @param maxDepth remaining search depth, in player moves (MAX plies) to look
     *                 ahead
     * @throws NullPointerException if {@code moved} is {@code null}
     */
    public ExpectedValue(Board moved, int maxDepth){
        this.moved = Objects.requireNonNull(moved,  "Moved board cannot be null");
        this.maxDepth = maxDepth;
    }

    /**
     * Computes the expected value of this task's board. Entry point when the task
     * is submitted to an executor; simply evaluates the CHANCE node at the root.
     *
     * @return the probability-weighted expected value of the board
     */
    @Override
    public Double call() throws Exception {
        return expectedValuesAfterMove(this.moved, this.maxDepth);
    }

    /**
     * CHANCE node of the expectimax tree: the expected value of a board on which a
     * new random tile is about to appear.
     * <p>
     * Every empty cell is considered as a possible spawn location, and for each the
     * two possible tile values are weighted by probability: a 2 with 90% and a 4
     * with 10%. Each resulting position is explored recursively via
     * {@link #bestMoveValue}, and the outcomes are averaged into a single expected
     * value. If the board has no empty cells, it is scored directly with
     * {@link #heuristic}.
     * <p>
     * The {@code depth} parameter bounds how much further the tree may be explored;
     * it is passed through unchanged here (a spawn is not a player move) and is
     * only decremented by the MAX node.
     *
     * @param board the board after the player's move, before a tile is added
     * @param depth the remaining search depth
     * @return the probability-weighted expected value over all possible spawns
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
     * MAX node of the expectimax tree: the value of the best move available from a
     * given board, from the player's point of view.
     * <p>
     * Each direction is tried; moves that do not change the board are skipped. Every
     * valid move is explored recursively through {@link #expectedValuesAfterMove}
     * (the following CHANCE node), and the highest value is returned. The recursion
     * stops — and the board is scored with {@link #heuristic} — when the remaining
     * depth reaches one or less, when the board is already game over, or when no
     * legal move exists.
     *
     * @param board the board to evaluate
     * @param depth remaining search depth, in player moves (MAX plies) still to look
     *              ahead; this node consumes one ply per recursive call
     * @return the value of the best legal move, or the heuristic score of the board
     *         when the depth limit is reached or no legal move exists
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
     * Leaf evaluation: scores a board so that higher values mean better positions.
     * Used when the search reaches its depth limit or cannot continue.
     * <p>
     * The score is a weighted sum of three features:
     * <ul>
     *   <li><b>Free space</b> ({@link #emptyCount}) — more empty cells score higher,
     *       leaving room to maneuver and lowering the risk of a dead end;</li>
     *   <li><b>Tile positioning</b> ({@link #snakeGradient}) — rewards large tiles
     *       arranged along the snake path toward a corner;</li>
     *   <li><b>Smoothness</b> ({@link #smoothness}) — rewards neighboring tiles of
     *       similar value, which merge more easily.</li>
     * </ul>
     * Only the relative weights matter, since {@link ExpectimaxAdvisor#suggest}
     * compares these scores to pick a move rather than using them absolutely.
     *
     * @param board the board to evaluate
     * @return a score whose higher values indicate more favorable positions
     */
    private double heuristic(Board board) {
        Integer[][] grid = board.toArray();
        return W_EMPTY * emptyCount(grid)
                + W_GRADIENT * snakeGradient(grid)
                + W_SMOOTH * smoothness(grid);
    }

    /**
     * Returns the base-2 rank (log2) of a tile value.
     * <p>
     * 2048 tiles are powers of two, so the rank is the exponent: 2&rarr;1, 4&rarr;2,
     * 8&rarr;3, 16&rarr;4, and so on. It is computed as the number of trailing zero
     * bits ({@link Integer#numberOfTrailingZeros(int)}), which equals log2 for
     * powers of two. An empty cell ({@code null}) has rank 0.
     *
     * @param value the tile value, or {@code null} for an empty cell
     * @return the base-2 rank of the tile, or 0 when the cell is empty
     */
    private static int rank(Integer value){
        return value == null ? 0 : Integer.numberOfTrailingZeros(value);
    }

    /**
     * Counts the empty cells on the grid.
     *
     * @param grid the board grid ({@code null} = empty cell)
     * @return the number of empty cells
     */
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
     * Scores how well tiles follow the {@link #SNAKE} positional gradient.
     * <p>
     * Each tile contributes its {@link #rank} multiplied by the snake weight of its
     * cell, so larger tiles in higher-weighted cells (near the preferred corner,
     * along the descending snake path) raise the score. This nudges the search to
     * keep the biggest tiles cornered and the rest ordered, which tends to keep the
     * board mergeable and avoids blocking it.
     *
     * @param grid the board grid
     * @return the snake-gradient score; higher means better-arranged tiles
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
     * Scores board smoothness: how close in value adjacent non-empty tiles are.
     * <p>
     * For each tile, the absolute rank difference to its right and lower neighbors
     * is subtracted from the score, so large differences penalize more. Empty cells
     * are skipped. Smoother boards score closer to zero and are generally better,
     * because similar neighbors merge more readily.
     *
     * @param grid the board grid
     * @return the smoothness score; values nearer zero indicate a smoother board,
     *         more negative values indicate larger differences between neighbors
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