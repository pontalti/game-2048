package com.pontalti.game2048.adapter.rest;

import com.pontalti.game2048.adapter.rest.dto.GameResponse;
import com.pontalti.game2048.adapter.rest.dto.HintResponse;
import com.pontalti.game2048.adapter.rest.dto.MoveRequest;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * REST adapter (inbound) for the 2048 game. This is the HTTP equivalent of the
 * console/Swing/JavaFX adapters: it translates HTTP requests into calls on the
 * domain via {@link GameService}, and never contains any game rules itself.
 * <p>
 * The domain ({@code Game}, {@code Board}, {@code Direction}) is unchanged — the
 * whole REST layer is additive, which is exactly what the hexagonal architecture
 * is meant to enable.
 */
@RestController
@RequestMapping("/games")
@Tag(name = "2048", description = "Play 2048 over HTTP")
public class GameController {

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new game", description = "Starts a fresh 2048 game with two random tiles.")
    @PostMapping
    public ResponseEntity<GameResponse> create(UriComponentsBuilder uri) {
        GameService.CreatedGame created = service.newGame();
        GameResponse body = GameResponse.from(created.id(), created.game());
        URI location = uri.path("/games/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(body); // 201 Created
    }

    @Operation(summary = "Get game state", description = "Returns the current board and status of a game.")
    @GetMapping("/{id}")
    public GameResponse get(@PathVariable String id) {
        return GameResponse.from(id, service.get(id));
    }

    @Operation(summary = "Play a move", description = "Applies a move; spawns a tile only if the board changed.")
    @PostMapping("/{id}/moves")
    public GameResponse move(@PathVariable String id, @Valid @RequestBody MoveRequest request) {
        Game game = service.move(id, request.direction());
        return GameResponse.from(id, game);
    }

    @Operation(summary = "Get an AI hint", description = "Suggests the best move without changing the game.")
    @GetMapping("/{id}/hint")
    public HintResponse hint(@PathVariable String id) {
        Optional<Direction> suggestion = service.hint(id);
        return new HintResponse(suggestion.map(Direction::name).orElse(null));
    }

    @Operation(summary = "Delete a game", description = "Removes a game from the server.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id); // 204 No Content
    }
}
