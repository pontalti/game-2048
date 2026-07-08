package com.pontalti.game2048.adapter.rest.service;

import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;

import java.util.Optional;

public interface GameService {

    CreatedGame newGame();
    Game get(String id);
    Game move(String id, Direction direction);
    Optional<Direction> hint(String id);
    void delete(String id);

    /** Carries a newly created game together with its generated id. */
    record CreatedGame(String id, Game game) {
    }
}
