package com.pontalti.game2048.domain;

/**
 * Result of a move: the resulting board plus the points earned by the merges
 * performed during that move.
 * <p>
 * Points are a pure function of (board, direction) — no state is accumulated
 * here. Whoever orchestrates the match (the {@code Game}) decides whether the
 * move was valid and adds the points to the player's running score. This keeps
 * {@code Board} immutable and, crucially, prevents the AI's simulated moves
 * from ever polluting the real score.
 *
 * @param board  the board after the move
 * @param points points earned by merges in this move (0 if nothing merged)
 */
public record MoveResult(Board board, int points) {

}
