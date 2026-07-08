package com.pontalti.game2048.adapter.rest.dto;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Game;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response describing the full state of a game: its id, the board grid, and the
 * current status. The board is exposed as a plain {@code Integer[][]} (null =
 * empty cell), mirroring the JSON format from the problem statement.
 *
 * @param id     the game identifier
 * @param board  the 4x4 grid; null entries are empty cells
 * @param status PLAYING, WON, or LOST
 */
public record GameResponse(
        @Schema(description = "Game id", example = "3f2504e0-4f89-41d3-9a0c-0305e82c3301")
        String id,
        @Schema(description = "4x4 grid; null = empty cell")
        Integer[][] board,
        @Schema(description = "Current status", example = "PLAYING")
        String status) {

    /** Builds a response from a domain {@link Game}. */
    public static GameResponse from(String id, Game game) {
        return new GameResponse(id, game.getBoard().toArray(), game.getStatus().name());
    }
}
