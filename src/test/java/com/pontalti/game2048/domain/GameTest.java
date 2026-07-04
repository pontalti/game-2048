package com.pontalti.game2048.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the orchestration behavior of the {@link Game} class.
 * <p>
 * The tests confirm that the game correctly evaluates and updates its current
 * status as the board changes.
 * <p>
 * The valid-move flow is tested in the expected order:
 * <ol>
 * <li>the player's move is applied;</li>
 * <li>a new random tile is added to the board;</li>
 * <li>the game status is evaluated again.</li>
 * </ol>
 * <p>
 * The tests also verify that invalid moves do not add a new tile or incorrectly
 * change the game state.
 * <p>
 * Terminal-state guards are covered to ensure that no additional moves are
 * processed after the game has ended.
 * <p>
 * A {@link Random} instance with a fixed seed is used so that tile generation
 * always follows the same sequence. This makes every test deterministic and
 * reproducible.
 */
public class GameTest {

    private static Random seeded() {
        return new Random(1234);
    }

    @Test
    @DisplayName("Constructor evaluates a winning board as WON")
    public void constructorDetectsWin() {
        Board won = new Board(new Integer[][]{
                {4, null, null, 2},
                {2048, null, null, null},
                {4, 2, null, null},
                {4, null, null, null}
        });
        Game game = new Game(won, seeded());
        assertEquals(GameStatus.WON, game.getStatus());
    }

    @Test
    @DisplayName("Constructor evaluates a dead-end board as LOST")
    public void constructorDetectsLoss() {
        Board lost = new Board(new Integer[][]{
                {2, 4, 2, 4},
                {4, 2, 4, 2},
                {2, 4, 2, 4},
                {4, 2, 4, 2}
        });
        Game game = new Game(lost, seeded());
        assertEquals(GameStatus.LOST, game.getStatus());
    }

    @Test
    @DisplayName("play() on an ended game is a no-op")
    public void playIgnoredWhenEnded() {
        Board lost = new Board(new Integer[][]{
                {2, 4, 2, 4},
                {4, 2, 4, 2},
                {2, 4, 2, 4},
                {4, 2, 4, 2}
        });
        Game game = new Game(lost, seeded());
        Board before = game.getBoard();

        assertEquals(GameStatus.LOST, game.play(Direction.LEFT));
        assertTrue(game.getBoard().sameGridAs(before));
    }

    @Test
    @DisplayName("An invalid move neither spawns a tile nor changes the status")
    public void invalidMoveDoesNothing() {
        Board packed = new Board(new Integer[][]{
                {2, 4, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
        });
        Game game = new Game(packed, seeded());
        Board before = game.getBoard();

        assertEquals(GameStatus.PLAYING, game.play(Direction.LEFT));
        assertTrue(game.getBoard().sameGridAs(before)); // no spawn happened
    }

    @Test
    @DisplayName("A valid move spawns exactly one new tile")
    public void validMoveSpawnsOneTile() {
        Board board = new Board(new Integer[][]{
                {2, 2, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
        });
        Game game = new Game(board, seeded());

        game.play(Direction.LEFT); // [2,2] merges to one 4, then one tile spawns
        assertEquals(2, nonNullCount(game.getBoard()));
        assertEquals(GameStatus.PLAYING, game.getStatus());
    }

    @Test
    @DisplayName("Merging into 2048 wins the game")
    public void mergingReaches2048() {
        Board board = new Board(new Integer[][]{
                {1024, 1024, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
        });
        Game game = new Game(board, seeded());

        assertEquals(GameStatus.WON, game.play(Direction.LEFT));
    }

    @Test
    @DisplayName("The no-arg-board constructor starts with two tiles")
    public void freshGameStartsWithTwoTiles() {
        Game game = new Game(seeded());
        assertEquals(2, nonNullCount(game.getBoard()));
        assertEquals(GameStatus.PLAYING, game.getStatus());
    }

    private static int nonNullCount(Board board) {
        int count = 0;
        for (Integer[] row : board.toArray()) {
            for (Integer v : row) {
                if (v != null) count++;
            }
        }
        return count;
    }
}