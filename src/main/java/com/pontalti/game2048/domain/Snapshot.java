package com.pontalti.game2048.domain;

/**
 * An immutable capture of everything a move changes, so that can
 * put the game back exactly as it was.
 *
 * @param board  the board before the move
 * @param score  the score before the move
 * @param status the status before the move
 */
public record Snapshot(Board board, int score, GameStatus status) {

}
