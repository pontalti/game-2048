package com.pontalti.game2048.adapter.rest.dto;

import com.pontalti.game2048.domain.Direction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for a move: the direction to play.
 *
 * @param direction the direction (LEFT, RIGHT, UP, DOWN)
 */
public record MoveRequest(
        @Schema(description = "Direction to move", example = "LEFT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "direction is required")
        Direction direction) {
}
