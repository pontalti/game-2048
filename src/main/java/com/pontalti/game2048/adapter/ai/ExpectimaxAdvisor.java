package com.pontalti.game2048.adapter.ai;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.port.out.MoveAdvisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Offline move advisor (requirement 6) based on the <b>expectimax</b> algorithm.
 * <p>
 * It analyzes the current board and suggests the best direction to move, without
 * using the internet, external services, or credentials.
 * <p>
 * <b>Why expectimax.</b> After the player moves, a new tile (2 or 4) appears in a
 * random empty cell, so there is no adversary actively trying to make the player
 * lose. Expectimax fits this: instead of assuming a worst-case opponent (as
 * minimax would), it takes the probability-weighted expectation over the random
 * spawns — a 2 with 90% and a 4 with 10%.
 * <p>
 * <b>Structure.</b> This class is the top-level <b>MAX</b> node and the
 * parallel coordinator; it does not contain the tree evaluation itself. For each
 * legal direction it creates an {@link ExpectedValue} task — the CHANCE node and
 * the deeper search for that move — and evaluates the four subtrees concurrently
 * on virtual threads. Because {@link Board} is immutable, the tasks share no
 * mutable state and need no synchronization.
 * <p>
 * <b>Depth.</b> The search is bounded by a depth measured in player moves. When
 * the limit is reached, {@link ExpectedValue} scores the board with its heuristic.
 * <p>
 * <b>Deterministic.</b> No random values are drawn during analysis — the
 * expectation is computed analytically. Therefore, for the same board,
 * {@link #suggest} always returns the same suggestion.
 */
public final class ExpectimaxAdvisor implements MoveAdvisor {

    /** Default look-ahead: the immediate move plus a couple of plies. */
    private static final int DEFAULT_DEPTH = 3;

    private final int maxDepth;

    /** Creates an advisor with the default search depth ({@value #DEFAULT_DEPTH}). */
    public ExpectimaxAdvisor(){
        this(DEFAULT_DEPTH);
    }

    /**
     * Creates an advisor with a custom search depth, measured in player moves
     * looked ahead. Larger values search deeper: stronger play, but slower.
     *
     * @param maxDepth number of player moves to look ahead (must be &gt;= 1)
     * @throws IllegalArgumentException if {@code maxDepth < 1}
     */
    public ExpectimaxAdvisor(int maxDepth){
        if (maxDepth < 1){
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        this.maxDepth = maxDepth;
    }

    /**
     * Top-level MAX node: suggests the legal move with the best expected outcome.
     * <p>
     * Each of the four directions is simulated; those that do not change the board
     * are illegal and are skipped, so the returned direction is always a valid move.
     * The remaining directions are scored in parallel (one {@link ExpectedValue}
     * task each) and the highest-scoring one is chosen. Ties are broken by
     * {@link Direction} declaration order, keeping the result deterministic.
     * <p>
     * If no direction changes the board, the position is a dead end and an empty
     * {@link Optional} is returned.
     *
     * @param board the current game board to analyze
     * @return the best valid direction, or an empty {@link Optional} when no valid
     *         move is available
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
            tasks.add(new ExpectedValue(moved, this.maxDepth));
        }

        if (legal.isEmpty()) {
            return Optional.empty(); // dead end
        }

        return getDirection(tasks, legal);
    }

    /**
     * Runs the scoring tasks concurrently and returns the direction with the best
     * score.
     * <p>
     * One virtual thread is used per task (via a try-with-resources executor that
     * awaits completion on close). Results are compared in the fixed order of
     * {@code legal}, and {@code best} is replaced only on a strictly greater score,
     * so ties resolve to the earliest direction and the outcome stays deterministic
     * despite parallel execution.
     *
     * @param tasks the scoring tasks, aligned by index with {@code legal}
     * @param legal the legal directions, in {@link Direction} order
     * @return the best-scoring direction; {@link Optional#empty()} only if
     *         {@code legal} is empty (never, given the caller's guard)
     * @throws IllegalStateException if the search is interrupted or a task fails
     */
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

}