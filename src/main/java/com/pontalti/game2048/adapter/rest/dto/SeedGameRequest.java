package com.pontalti.game2048.adapter.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the test-only "seed" endpoint: a fully specified 4x4 board to
 * plant as a game's starting state. Cells are tile values; {@code null} means an
 * empty cell (same contract as {@link GameResponse#board()}).
 *
 * @param board a 4x4 grid; null entries are empty cells
 */
public record SeedGameRequest(
        @Schema(description = "4x4 grid to plant; null = empty cell")
        @NotNull(message = "board is required")
        Integer[][] board) {
}