package com.pontalti.game2048.domain.port.out;

import com.pontalti.game2048.domain.Game;

import java.util.Optional;

/**
 * Output port (hexagonal architecture): a place to store and retrieve games by
 * id, without the domain knowing how or where they are persisted.
 * <p>
 * HTTP is stateless — each request is independent — but a 2048 match is
 * stateful. This port bridges that gap: the REST layer creates a game, saves it
 * under an id, and looks it up again on the next request. The domain declares
 * the need; an adapter (in-memory map, Redis, a database, ...) provides the how.
 */
public interface GameRepository {

    /**
     * Stores a new game and returns the id assigned to it.
     *
     * @param game the game to store
     * @return the generated identifier
     */
    String create(Game game);

    /**
     * Looks up a game by id.
     *
     * @param id the game identifier
     * @return the game, or {@link Optional#empty()} if no game has that id
     */
    Optional<Game> findById(String id);

    /**
     * Removes a game.
     *
     * @param id the game identifier
     * @return {@code true} if a game was removed, {@code false} if none existed
     */
    boolean deleteById(String id);
}
