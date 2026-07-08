package com.pontalti.game2048.adapter.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for a hint request: the suggested direction, or null when the
 * position is a dead end (no move changes the board).
 *
 * @param suggestion the suggested direction (LEFT/RIGHT/UP/DOWN), or null
 */
public record HintResponse(
        @Schema(description = "Suggested direction, or null at a dead end", example = "UP")
        String suggestion) {
}
