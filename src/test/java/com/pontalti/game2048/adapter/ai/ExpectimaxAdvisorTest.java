package com.pontalti.game2048.adapter.ai;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.port.MoveAdvisor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the behavior of the {@link ExpectimaxAdvisor} required by
 * requirement 6.
 * <p>
 * The tests confirm that the {@code suggest} method follows its main contract:
 * <ul>
 * <li>
 * it only returns a legal direction that actually changes the board;
 * </li>
 * <li>
 * it returns an empty result when the board has reached a dead end and no
 * valid move is available;
 * </li>
 * <li>
 * it always returns the same suggestion for the same board state.
 * </li>
 * </ul>
 * <p>
 * The determinism test is important because the advisor evaluates random tile
 * spawns using probabilities rather than generating random values during the
 * search.
 * <p>
 * Together, these tests ensure that the advisor produces valid, predictable,
 * and safe move suggestions without modifying the game board.
 */
public class ExpectimaxAdvisorTest {

    private final MoveAdvisor advisor = new ExpectimaxAdvisor();

    @Test
    @DisplayName("Suggests a legal move on a normal board")
    public void suggestsLegalMove() {
        Board board = new Board(new Integer[][]{
                {2, null, 4, null},
                {null, 8, null, 16},
                {32, null, null, 2},
                {null, 4, 128, null}
        });
        Optional<Direction> suggestion = advisor.suggest(board);

        assertTrue(suggestion.isPresent());
        // A legal move must actually change the board.
        assertFalse(board.move(suggestion.get()).sameGridAs(board));
    }

    @Test
    @DisplayName("Returns empty on a game-over board")
    public void emptyOnGameOver() {
        Board lost = new Board(new Integer[][]{
                {2, 4, 2, 4},
                {4, 2, 4, 2},
                {2, 4, 2, 4},
                {4, 2, 4, 2}
        });
        assertTrue(advisor.suggest(lost).isEmpty());
    }

    @Test
    @DisplayName("Picks the only legal move in a forced position")
    public void picksForcedMove() {
        // Only DOWN changes this board; LEFT/RIGHT/UP leave it identical.
        Board forced = new Board(new Integer[][]{
                {2, 4, 8, 16},
                {4, 8, 16, 32},
                {8, 16, 32, 64},
                {null, null, null, null}
        });
        // Sanity-check the premise, then the advisor's choice.
        assertTrue(forced.move(Direction.LEFT).sameGridAs(forced));
        assertTrue(forced.move(Direction.RIGHT).sameGridAs(forced));
        assertTrue(forced.move(Direction.UP).sameGridAs(forced));
        assertFalse(forced.move(Direction.DOWN).sameGridAs(forced));

        assertEquals(Optional.of(Direction.DOWN), advisor.suggest(forced));
    }

    @Test
    @DisplayName("Is deterministic: same board yields the same suggestion")
    public void isDeterministic() {
        Board board = new Board(new Integer[][]{
                {2, null, 4, null},
                {null, 8, null, 16},
                {32, null, null, 2},
                {null, 4, 128, null}
        });
        assertEquals(advisor.suggest(board), advisor.suggest(board));
    }

    @Test
    @DisplayName("Constructor rejects a depth below 1")
    public void constructorRejectsInvalidDepth() {
        assertThrows(IllegalArgumentException.class, () -> new ExpectimaxAdvisor(0));
        assertThrows(IllegalArgumentException.class, () -> new ExpectimaxAdvisor(-1));
    }

    @Test
    @DisplayName("A custom depth still produces a legal suggestion")
    public void customDepthWorks() {
        MoveAdvisor deep = new ExpectimaxAdvisor(4);
        Board board = new Board(new Integer[][]{
                {2, 2, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
        });
        Optional<Direction> suggestion = deep.suggest(board);
        assertTrue(suggestion.isPresent());
        assertFalse(board.move(suggestion.get()).sameGridAs(board));
    }
}