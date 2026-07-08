package com.pontalti.game2048.adapter.rest;

import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.port.GameRepository;
import com.pontalti.game2048.domain.port.MoveAdvisor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

/**
 * Application service for the REST adapter. It coordinates the repository (where
 * games live between requests), the domain ({@link Game}), and the AI
 * ({@link MoveAdvisor}) — but contains no game rules itself. All rules stay in
 * the domain; this class only wires a stateless HTTP world to a stateful game.
 * <p>
 * <b>Concurrency:</b> a {@link Game} is mutable and single-threaded by design,
 * while a web server handles requests in parallel. Moves on the same game are
 * therefore serialized with {@code synchronized (game)} so two concurrent
 * requests on one match cannot interleave and corrupt its state. Different games
 * lock on different instances, so they still run fully in parallel.
 */
@Service
public class GameService {

    private final GameRepository repository;
    private final MoveAdvisor advisor;
    private final Random random;

    public GameService(GameRepository repository, MoveAdvisor advisor, Random random) {
        this.repository = repository;
        this.advisor = advisor;
        this.random = random;
    }

    /** Creates a fresh game (two starting tiles) and returns its id + the game. */
    public CreatedGame newGame() {
        Game game = new Game(random);
        String id = repository.create(game);
        return new CreatedGame(id, game);
    }

    /** Returns the game for the given id, or throws {@link GameNotFoundException}. */
    public Game get(String id) {
        return repository.findById(id).orElseThrow(() -> new GameNotFoundException(id));
    }

    /**
     * Applies a move to the game with the given id and returns the updated game.
     * Serialized per game instance to stay thread-safe under concurrent requests.
     */
    public Game move(String id, Direction direction) {
        Game game = get(id);
        synchronized (game) {
            game.play(direction);
            return game;
        }
    }

    /** Returns the AI's suggested move for the given game (does not modify it). */
    public Optional<Direction> hint(String id) {
        Game game = get(id);
        synchronized (game) {
            return advisor.suggest(game.getBoard());
        }
    }

    /** Deletes the game; throws {@link GameNotFoundException} if it did not exist. */
    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new GameNotFoundException(id);
        }
    }

    /** Small carrier pairing a new game with its generated id. */
    public record CreatedGame(String id, Game game) {
    }
}
