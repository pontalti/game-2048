package com.pontalti.game2048.adapter.ui.console;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.GameStatus;
import com.pontalti.game2048.domain.port.MoveAdvisor;

import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/**
 * Console (text) input adapter that drives a {@link Game} through a classic
 * game loop: render → read input → translate → play → repeat, until the game
 * is WON or LOST (or the player quits / input ends).
 * <p>
 * <b>Boundary role:</b> this is the ONLY place that knows the mapping between
 * physical keys (W/A/S/D) and {@link Direction}. The domain never sees a
 * keystroke — it only receives an already-translated {@code Direction}. Swapping
 * this console adapter for a Swing/JavaFX one later means rewriting only this
 * class; {@link Game} stays untouched.
 * <p>
 * <b>AI hint:</b> the advisor is used through the {@link MoveAdvisor} port, not
 * its concrete implementation, so the UI depends only on the abstraction. The
 * hint command reads the board and prints a suggestion without advancing the game.
 * <p>
 * <b>Presentation lives here:</b> rendering the grid is the UI's job, not the
 * {@code Board}'s. ({@code Board.toString} remains only a debug aid.)
 * <p>
 * <b>Injected dependencies:</b> {@link Game}, {@link MoveAdvisor} and
 * {@link Scanner} are all passed in, so tests can drive the loop with scripted
 * input and {@code Main} owns the wiring and the lifecycle of {@code System.in}.
 */
public final class ConsoleUI {

    private final Game game;
    private final MoveAdvisor advisor;
    private final Scanner scanner;

    public ConsoleUI(Game game, MoveAdvisor advisor, Scanner scanner){
        this.game = Objects.requireNonNull(game, "game cannot be null");
        this.advisor = Objects.requireNonNull(advisor, "advisor cannot be null");
        this.scanner = Objects.requireNonNull(scanner, "scanner cannot be null");
    }

    /**
     * Starts and manages the main game loop.
     * <p>
     * During each iteration, the method displays the current board, reads the
     * player's input, and processes the requested command or movement.
     * <p>
     * The loop continues until one of the following conditions occurs:
     * <ul>
     * <li>the game reaches a terminal state and no valid moves remain;</li>
     * <li>the player chooses to quit the game;</li>
     * <li>the input stream is closed and no more commands can be read.</li>
     * </ul>
     * <p>
     * This method coordinates the console interaction and delegates the game rules
     * and move processing to the appropriate domain objects.
     */
    public void run(){
        printInstructions();

        while(this.game.getStatus() == GameStatus.PLAYING){
            render(this.game.getBoard());

            /*
                EOF (Ctrl+D or a piped input that ran out): exit gracefully
                instead of throwing NoSuchElementException.
            */
            if(!this.scanner.hasNextLine()){
                System.out.println("\nInput closed. Bye!");
                return;
            }
            String input = this.scanner.nextLine().trim().toLowerCase();

            if(input.equals("q")){
                System.out.println("You quit. Bye!");
                return;
            }

            //Hint command: consult the AI advisor without consuming a turn.
            if (input.equals("h")) {
                printHint();
                continue;
            }

            Direction dir = toDirection(input);
            if(dir == null){
                System.out.println("Invalid key. Use W/A/S/D to move, H for a hint, or Q to quit.");
                continue;
            }
            /*
                Capture the board reference before playing so we can tell whether
                the move actually changed anything (Board is immutable, so a valid
                move reassigns a new instance; an invalid one leaves it identical).
            */
            Board before = this.game.getBoard();
            game.play(dir);
            if(game.getBoard().sameGridAs(before)){
                System.out.println("That move changes nothing — try another direction.");
            }
        }

        // Loop exited because the game ended: show the final board and outcome.
        render(this.game.getBoard());
        printResult(this.game.getStatus());
    }

    /**
     * Requests a move suggestion for the current board and displays it to the
     * player.
     * <p>
     * The suggestion is provided by the configured {@link MoveAdvisor}, which
     * analyzes the current board and returns the best available direction.
     * <p>
     * This method does not perform the suggested move and does not modify the game
     * state. It only prints the recommendation so that the player can decide
     * whether to follow it.
     * <p>
     * If the advisor cannot find a valid move because the board has reached a dead
     * end, it returns an empty result and this method displays an appropriate
     * message.
     */
    private void printHint(){
        Optional<Direction> suggestion = this.advisor.suggest(this.game.getBoard());
        if(suggestion.isPresent()) {
            Direction dir = suggestion.get();
            System.out.println("Hint: press " + keyFor(dir) + " to move " + dir + ".");
        }else {
            System.out.println("Hint: no move can change the board.");
        }
    }

    /**
     * Returns the keyboard key associated with the specified movement direction.
     * <p>
     * This method performs the reverse conversion of {@link #toDirection}. While
     * {@code toDirection} converts a pressed key into a {@link Direction}, this
     * method converts a direction back into the key the player should press.
     * <p>
     * It is mainly used when displaying hints, allowing the game to show both the
     * recommended direction and the corresponding keyboard key.
     *
     * @param dir the movement direction to convert
     * @return the keyboard key associated with the specified direction
     */
    private static String keyFor(Direction dir){
        return switch (dir){
            case UP -> "W";
            case DOWN -> "S";
            case LEFT -> "A";
            case RIGHT -> "D";
        };
    }

    /**
     * Converts a raw console input value into the corresponding movement direction.
     * <p>
     * This method contains the keyboard mapping used by the console interface. It
     * checks whether the provided input matches one of the supported movement keys
     * and returns the associated {@link Direction}.
     * <p>
     * Inputs that do not represent a movement command are not converted. In that
     * case, the method returns {@code null}, allowing the caller to handle other
     * commands or invalid input separately.
     *
     * @param input the raw text entered by the player
     * @return the corresponding movement direction, or {@code null} when the input
     * is not a recognized movement key
     */
    private static Direction toDirection(String input){
        return switch (input) {
            case "w" -> Direction.UP;
            case "s" -> Direction.DOWN;
            case "a" -> Direction.LEFT;
            case "d" -> Direction.RIGHT;
            default -> null;
        };
    }

    /**
     * Displays the current board as a simple grid with visible borders.
     * <p>
     * Each board cell is rendered in its corresponding row and column. Cells
     * containing tiles display their numeric values, while empty cells are shown as
     * blank spaces.
     * <p>
     * This method is responsible only for the visual representation of the board in
     * the console. It does not modify the board or any other part of the game state.
     *
     * @param board the current game board to display
     */
    private static void render(Board board){
        Integer[][] grid = board.toArray();
        String border = "+------+------+------+------+";
        StringBuilder sb = new StringBuilder("\n");
        for(Integer[] row : grid){
            sb.append(border).append("\n");
            for(Integer value : row){
                sb.append(String.format("| %4s ", value == null ? "" : value));
            }
            sb.append("|\n");
        }
        sb.append(border);
        System.out.println(sb);
    }

    private static void printInstructions(){
        System.out.println("=== 2048 ===");
        System.out.println("Move: W=up  A=left  S=down  D=right  (press Enter after each key)");
        System.out.println("H=hint (ask the AI)   Q=quit.   Reach 2048 to win!");
    }

    private static void printResult(GameStatus status){
        switch (status){
            case WON -> System.out.println("\nYou reached 2048 - you win!");
            case LOST -> System.out.println("\nNo moves left - game over!");
            case PLAYING -> {/* required for exhaustiveness; never reached at runtime */}
        }
    }

}
