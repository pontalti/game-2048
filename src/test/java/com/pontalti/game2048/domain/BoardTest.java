package com.pontalti.game2048.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the behavior of the {@link Board} class using the examples defined
 * in the problem statement and additional edge cases.
 * <p>
 * The tests cover the main board operations required by requirements 2 through
 * 6, including tile movement, tile merging, score calculation, valid-move
 * detection, and game-state evaluation.
 * <p>
 * Additional tests verify important implementation rules:
 * <ul>
 * <li>
 * <b>Single-merge rule:</b> a tile created by a merge cannot merge again
 * during the same move.
 * </li>
 * <li>
 * <b>Immutability:</b> operations that produce a new board state do not
 * modify the original board.
 * </li>
 * <li>
 * <b>Defensive copying:</b> external changes to arrays used to create or
 * retrieve the board do not affect its internal state.
 * </li>
 * </ul>
 * <p>
 * Together, these tests ensure that the board follows the expected 2048 rules
 * and protects its internal data from unintended modification.
 */
public class BoardTest {

    @Nested
    @DisplayName("Moves (requirements 2, 3, 4)")
    class Moves {

        /** The shared "before" board used by the LEFT/RIGHT/UP examples in the statement. */
        private Board sample() {
            return new Board(new Integer[][]{
                    {null, 8, 2, 2},
                    {4, 2, null, 2},
                    {null, null, null, null},
                    {null, null, null, 2}
            });
        }

        @Test
        @DisplayName("Move Left matches the statement example")
        public void moveLeft() {
            Board expected = new Board(new Integer[][]{
                    {8, 4, null, null},
                    {4, 4, null, null},
                    {null, null, null, null},
                    {2, null, null, null}
            });
            assertEquals(expected, sample().move(Direction.LEFT));
        }

        @Test
        @DisplayName("Move Right matches the statement example")
        public void moveRight() {
            Board expected = new Board(new Integer[][]{
                    {null, null, 8, 4},
                    {null, null, 4, 4},
                    {null, null, null, null},
                    {null, null, null, 2}
            });
            assertEquals(expected, sample().move(Direction.RIGHT));
        }

        @Test
        @DisplayName("Move Up matches the statement example")
        public void moveUp() {
            Board expected = new Board(new Integer[][]{
                    {4, 8, 2, 4},
                    {null, 2, null, 2},
                    {null, null, null, null},
                    {null, null, null, null}
            });
            assertEquals(expected, sample().move(Direction.UP));
        }

        @Test
        @DisplayName("Move Down is consistent with the derived transform")
        public void moveDown() {
            Board expected = new Board(new Integer[][]{
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, 8, null, 2},
                    {4, 2, 2, 4}
            });
            assertEquals(expected, sample().move(Direction.DOWN));
        }

        @Test
        @DisplayName("A tile merges at most once per move: [2,2,2,2] -> [4,4,-,-]")
        public void singleMergePerMove() {
            Board before = new Board(new Integer[][]{
                    {2, 2, 2, 2},
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null}
            });
            Board expected = new Board(new Integer[][]{
                    {4, 4, null, null},
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null}
            });
            assertEquals(expected, before.move(Direction.LEFT));
        }

        @Test
        @DisplayName("A move that changes nothing is reported by sameGridAs")
        public void invalidMoveIsDetected() {
            Board packed = new Board(new Integer[][]{
                    {2, 4, null, null},
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null}
            });
            assertTrue(packed.move(Direction.LEFT).sameGridAs(packed));
        }
    }

    @Nested
    @DisplayName("Endgame (requirement 6)")
    class Endgame {

        @Test
        @DisplayName("A full board with no equal neighbors is game over")
        public void loseCondition() {
            Board lost = new Board(new Integer[][]{
                    {2, 4, 2, 4},
                    {4, 2, 4, 2},
                    {2, 4, 2, 4},
                    {4, 2, 4, 2}
            });
            assertTrue(lost.isGameOver());
            assertFalse(lost.hasWon());
        }

        @Test
        @DisplayName("A board containing 2048 is a win")
        public void winCondition() {
            Board won = new Board(new Integer[][]{
                    {4, null, null, 2},
                    {2048, null, null, null},
                    {4, 2, null, null},
                    {4, null, null, null}
            });
            assertTrue(won.hasWon());
        }

        @Test
        @DisplayName("A board with empty cells is never game over")
        public void notOverWhenEmptyExists() {
            Board withSpace = new Board(new Integer[][]{
                    {2, 4, 2, 4},
                    {4, 2, 4, 2},
                    {2, 4, 2, 4},
                    {4, 2, 4, null}
            });
            assertFalse(withSpace.isGameOver());
        }
    }

    @Nested
    @DisplayName("Immutability & defensive copy")
    class Immutability {

        @Test
        @DisplayName("move() does not mutate the original board")
        public void moveDoesNotMutate() {
            Board original = new Board(new Integer[][]{
                    {2, 2, null, null},
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null}
            });
            Integer[][] snapshot = original.toArray();
            original.move(Direction.LEFT);
            assertArrayEquals(snapshot, original.toArray());
        }

        @Test
        @DisplayName("Mutating the source array after construction does not affect the board")
        public void defensiveCopyOnConstruction() {
            Integer[][] source = {
                    {2, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null}
            };
            Board board = new Board(source);
            source[0][0] = 9999; // tamper after construction
            assertEquals(2, board.toArray()[0][0]);
        }

        @Test
        @DisplayName("toArray() returns a copy, not the internal array")
        public void toArrayReturnsCopy() {
            Board board = new Board(new Integer[][]{
                    {2, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null},
                    {null, null, null, null}
            });
            Integer[][] exported = board.toArray();
            exported[0][0] = 9999; // tamper with the exported copy
            assertEquals(2, board.toArray()[0][0]);
        }
    }

    @Nested
    @DisplayName("Spawn (requirements 1, 5)")
    class Spawn {

        @Test
        @DisplayName("spawnRandom fills exactly one empty cell")
        public void spawnFillsOneCell() {
            Board empty = Board.empty();
            Board afterOne = empty.spawnRandom(new Random(1));
            assertEquals(1, nonNullCount(afterOne));
        }

        @Test
        @DisplayName("spawnRandom on a full board returns the same board")
        public void spawnOnFullBoardIsNoOp() {
            Board full = new Board(new Integer[][]{
                    {2, 4, 2, 4},
                    {4, 2, 4, 2},
                    {2, 4, 2, 4},
                    {4, 2, 4, 2}
            });
            assertSame(full, full.spawnRandom(new Random(1)));
        }

        @Test
        @DisplayName("A spawned tile is always 2 or 4")
        public void spawnedTileIsTwoOrFour() {
            Board board = Board.empty().spawnRandom(new Random(42));
            Integer tile = firstNonNull(board);
            assertTrue(tile != null && (tile == 2 || tile == 4),
                    () -> "spawned tile should be 2 or 4 but was " + tile);
        }
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

    private static Integer firstNonNull(Board board) {
        for (Integer[] row : board.toArray()) {
            for (Integer v : row) {
                if (v != null) return v;
            }
        }
        return null;
    }
}