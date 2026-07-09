package com.pontalti.game2048.domain.port.in;

import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;

import java.util.Optional;

/**
 * Input port (driving side of the hexagon): the use cases the application offers
 * to the outside world for playing 2048.
 * <p>
 * Inbound adapters — the REST controller here, but equally a CLI or a UI — depend
 * on this interface, not on any concrete implementation. The implementation
 * ({@code GameServiceImpl}) lives in the adapter/application layer and wires this
 * port to the domain and to the output ports (repository, advisor).
 * <p>
 * This mirrors the output ports ({@code GameRepository}, {@code MoveAdvisor}):
 * every crossing of the hexagon's boundary, in either direction, goes through a
 * port owned by the domain.
 */
public interface GamePort {

    /**
     * Creates a fresh game (two starting tiles).
     *
     * @return the new game together with its generated id
     */
    CreatedGame newGame();

    /**
     * Returns the current state of a game.
     *
     * @param id the game id
     * @return the game
     */
    Game get(String id);

    /**
     * Applies a move to a game and returns the updated state.
     *
     * @param id        the game id
     * @param direction the direction to play
     * @return the updated game
     */
    Game move(String id, Direction direction);

    /**
     * Returns the AI-suggested move for a game, without changing it.
     *
     * @param id the game id
     * @return the suggested direction, or {@link Optional#empty()} at a dead end
     */
    Optional<Direction> hint(String id);

    /**
     * Deletes a game.
     *
     * @param id the game id
     */
    void delete(String id);

    /** Carries a newly created game together with its generated id. */
    record CreatedGame(String id, Game game) {
    }
}