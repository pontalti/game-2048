package com.pontalti.game2048.adapter.rest;

import com.pontalti.game2048.adapter.rest.dto.GameResponse;
import com.pontalti.game2048.adapter.rest.dto.SeedGameRequest;
import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.port.GameRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Random;

/**
 * Test-only HTTP support. This controller lets a client create a game from a
 * <b>specific</b> starting board, which makes deterministic win/loss scenarios
 * reachable purely over HTTP (the normal {@code POST /games} is random).
 * <p>
 * <b>It is guarded by {@code @Profile("test")}</b>: Spring only registers this
 * bean when the {@code test} profile is active. In production the profile is not
 * set, so this endpoint does not exist at all — a request to it returns 404.
 * That keeps the public production API free of any test-only surface while still
 * allowing end-to-end tests to drive everything through HTTP.
 */
@RestController
@RequestMapping("/test/games")
@Profile("test")
@Tag(name = "test-support", description = "Test-only endpoints (active only under the 'test' profile)")
public class TestSupportController {

    private final GameRepository repository;

    public TestSupportController(GameRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Create a game from a specific board (test only)",
            description = "Plants a game whose starting board is exactly the one provided.")
    @PostMapping
    public ResponseEntity<GameResponse> seed(@Valid @RequestBody SeedGameRequest request,
                                             UriComponentsBuilder uri) {
        // Fixed-seed Random keeps any later spawns reproducible in tests.
        Game game = new Game(new Board(request.board()), new Random(0));
        String id = repository.create(game);
        GameResponse body = GameResponse.from(id, game);
        URI location = uri.path("/games/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }
}