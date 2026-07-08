package com.pontalti.game2048.adapter.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Uniform error body returned for 4xx responses.
 *
 * @param error   short error code
 * @param message human-readable detail
 */
public record ErrorResponse(
        @Schema(example = "NOT_FOUND") String error,
        @Schema(example = "No game with id 3f2504e0...") String message) {
}
