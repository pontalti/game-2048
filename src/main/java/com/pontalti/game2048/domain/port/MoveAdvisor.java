package com.pontalti.game2048.domain.port;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;

import java.util.Optional;

/**
 * Output port (hexagonal architecture): the domain's request for a move
 * suggestion, without knowing how that suggestion is computed.
 * <p>
 * Implementations live in the adapter layer — e.g. an offline expectimax
 * search ({@code ExpectimaxAdvisor}), or, hypothetically, a remote AI service.
 * Keeping this an interface lets us swap the strategy (or mock it in tests)
 * without touching the game core.
 */
public interface MoveAdvisor {

    /**
     * Suggests the best move for the given board.
     *
     * @param board current board
     * @return the recommended {@link Direction}, or {@link Optional#empty()} if
     *         no move is possible (i.e. the position is a dead end)
     */
    Optional<Direction> suggest(Board board);
}
