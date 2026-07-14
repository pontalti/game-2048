package com.pontalti.game2048.adapter.ui.fx;

import com.pontalti.game2048.adapter.ai.ExpectimaxAdvisor;
import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.GameStatus;
import com.pontalti.game2048.domain.port.out.MoveAdvisor;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.Random;

/**
 * Provides the JavaFX desktop interface for the 2048 game.
 * <p>
 * This class acts as an adapter between the graphical user interface and the
 * game domain. Like {@code ConsoleUI} and the Swing adapter, it controls the
 * same {@link Game} without changing or duplicating any game rules.
 * <p>
 * JavaFX is event-driven. Keyboard events and button actions are translated
 * into operations such as moving a tile, requesting a hint, or starting a new
 * game. Movement directions are passed to {@link Game#play(Direction)}, while
 * the remaining actions affect only the adapter state.
 * <p>
 * The keyboard mapping and all visual behavior remain confined to this class,
 * keeping JavaFX concerns separate from the domain logic.
 * <p>
 * All handlers run on the JavaFX Application Thread. Therefore, the
 * single-threaded {@link Game} can be accessed safely without additional
 * synchronization.
 */
public class FxUI extends Application {

    private Game game = new Game(new Random());
    private final MoveAdvisor advisor = new ExpectimaxAdvisor();

    private final Label[][] cells = new Label[Board.SIZE][Board.SIZE];
    private Label statusLabel;

    /**
     * Displays the player's running score.
     * <p>
     * Like {@link #statusLabel}, it is a field rather than a local variable inside
     * {@link #start(Stage)} so that {@link #render()} can refresh it after every
     * move and whenever a new game begins.
     */
    private Label scoreLabel;

    /**
     * Creates, configures, and displays the main JavaFX game window.
     * <p>
     * This method builds the board grid, score line, action buttons, status line,
     * keyboard controls, and initial visual state.
     * <p>
     * JavaFX invokes this method on the JavaFX Application Thread after the
     * application is launched.
     *
     * @param stage the primary window supplied by the JavaFX runtime
     */
    @Override
    public void start(Stage stage) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.setAlignment(Pos.CENTER);

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Label cell = new Label();
                cell.setMinSize(80, 80);
                cell.setAlignment(Pos.CENTER);
                cell.setFont(Font.font(26));
                cells[r][c] = cell;
                grid.add(cell, c, r);
            }
        }

        // Score sits right under the board, in bold so it reads at a glance.
        scoreLabel = new Label("Score: 0");
        scoreLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));

        statusLabel = new Label();
        statusLabel.setFont(Font.font(16));

        Button newGameButton = new Button("New Game (N)");
        newGameButton.setFocusTraversable(false);
        newGameButton.setOnAction(event -> newGame());

        Button hintButton = new Button("Hint (H)");
        hintButton.setFocusTraversable(false);
        hintButton.setOnAction(event -> showHint());

        Button undoButton = new Button("Undo (U)");
        undoButton.setFocusTraversable(false);
        undoButton.setOnAction(event -> undo());

        HBox actions = new HBox(10, newGameButton, hintButton, undoButton);
        actions.setAlignment(Pos.CENTER);

        Label help = new Label("Arrows or WASD to move  ·  H for a hint  ·  U to undo  ·  N for a new game");

        // The VBox is aligned to CENTER, so every child — including the score — is
        // horizontally centered without any extra configuration.
        VBox root = new VBox(12, grid, scoreLabel, actions, statusLabel, help);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(16));

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(this::onKeyPressed);

        stage.setTitle("2048");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        render();
    }

    /**
     * Handles keyboard commands and delegates them to the appropriate adapter
     * action.
     * <p>
     * The H key displays a hint, the N key starts a new game, and the arrow or
     * WASD keys are converted into movement directions.
     * <p>
     * Recognized events are consumed so that arrow keys do not trigger focus
     * traversal or other default interface behavior.
     *
     * @param event the keyboard event received by the scene
     */
    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.H) {
            showHint();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.N) {
            newGame();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.U) {
            undo();
            event.consume();
            return;
        }

        Direction direction = toDirection(event.getCode());
        if (direction == null) {
            return;
        }

        event.consume();
        play(direction);
    }

    /**
     * Converts a JavaFX key code into the corresponding movement direction.
     * <p>
     * Both the arrow keys and the W, A, S, and D keys are supported. Keys that
     * do not represent movement commands are not converted.
     *
     * @param code the key code generated by JavaFX
     * @return the corresponding direction, or {@code null} when the key is not
     *         a recognized movement command
     */
    private static Direction toDirection(KeyCode code) {
        return switch (code) {
            case UP, W -> Direction.UP;
            case DOWN, S -> Direction.DOWN;
            case LEFT, A -> Direction.LEFT;
            case RIGHT, D -> Direction.RIGHT;
            default -> null;
        };
    }

    /**
     * Applies the specified move and refreshes the user interface.
     * <p>
     * Before processing the move, this method verifies that the game is still
     * running. Requests made after the game has ended are ignored.
     * <p>
     * When the game is active, the direction is passed to
     * {@link Game#play(Direction)}, which updates the board, awards the points
     * earned by any merges, spawns a new tile when appropriate, and re-evaluates
     * the status. The interface is then rendered again to display the latest
     * board, score and game status.
     *
     * @param direction the movement direction requested by the player
     */
    private void play(Direction direction) {
        if (game.getStatus() != GameStatus.PLAYING) {
            return;
        }

        game.play(direction);
        render();
    }

    /**
     * Rolls the game back to the state before the last valid move and refreshes the
     * interface.
     * <p>
     * The domain decides whether an undo is possible: {@link Game#undo()} restores
     * the board, the score and the status together, and reports whether it did
     * anything. This adapter only reflects that outcome, so the button and the
     * {@code U} key behave identically — both call this one method.
     * <p>
     * The message is written <b>after</b> {@link #render()}, because render clears
     * the status line whenever the game is {@code PLAYING}; setting it first would
     * wipe the message immediately.
     * <p>
     * A move that changed nothing never creates an undo point, so it can never be
     * undone — there is nothing to roll back to.
     */
    private void undo() {
        boolean undone = game.undo();
        render();
        statusLabel.setText(undone ? "Move undone" : "Nothing to undo");
    }

    /**
     * Requests a move suggestion from the configured advisor and displays it to
     * the player.
     * <p>
     * The advisor analyzes the current board and returns the best available
     * direction according to its evaluation strategy.
     * <p>
     * This method does not execute the suggested move and does not change the
     * game state — asking for a hint never affects the score. If no legal move
     * exists, an appropriate message is displayed.
     */
    private void showHint() {
        advisor.suggest(game.getBoard()).ifPresentOrElse(
                direction -> statusLabel.setText("Hint: move " + direction),
                () -> statusLabel.setText("Hint: no move changes the board"));
    }

    /**
     * Starts a new game and refreshes the user interface.
     * <p>
     * A new {@link Game} instance replaces the current game, restoring the
     * initial board and status — and resetting the score to zero, which
     * {@link #render()} then reflects on screen.
     * <p>
     * Only the adapter state is replaced. The domain implementation remains
     * unchanged, and no game rules are duplicated in this method.
     */
    private void newGame() {
        game = new Game(new Random());
        render();
    }

    /**
     * Refreshes the graphical interface using the current game state.
     * <p>
     * Every cell is updated with its latest tile value and visual style. Empty
     * cells are displayed without text.
     * <p>
     * The score line is refreshed from the domain, and the status line is updated
     * to indicate whether the game is still in progress, has been won, or has ended
     * because no moves remain. Because every state-changing action ({@link #play}
     * and {@link #newGame}) calls this method, the score on screen always matches
     * the domain.
     * <p>
     * This method changes only the visual representation and does not modify
     * the board or any other domain state.
     */
    private void render() {
        Integer[][] grid = game.getBoard().toArray();

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Integer value = grid[r][c];
                Label cell = cells[r][c];
                cell.setText(value == null ? "" : String.valueOf(value));
                cell.setStyle(styleFor(value));
            }
        }

        scoreLabel.setText("Score: " + game.getScore());

        switch (game.getStatus()) {
            case WON -> statusLabel.setText("You win!");
            case LOST -> statusLabel.setText("Game over — no moves left");
            case PLAYING -> statusLabel.setText("");
        }
    }

    /**
     * Returns the JavaFX style associated with the specified tile value.
     * <p>
     * Each tile value is mapped to a background and text color inspired by the
     * original 2048 palette. Empty cells receive the default board color, while
     * values above 2048 use a generic high-value style.
     *
     * @param value the tile value, or {@code null} for an empty cell
     * @return the JavaFX CSS style used to render the tile
     */
    private static String styleFor(Integer value) {
        String background = switch (value == null ? 0 : value) {
            case 0 -> "#cdc1b4";
            case 2 -> "#eee4da";
            case 4 -> "#ede0c8";
            case 8 -> "#f2b179";
            case 16 -> "#f59563";
            case 32 -> "#f67c5f";
            case 64 -> "#f65e3b";
            case 128 -> "#edcf72";
            case 256 -> "#edcc61";
            case 512 -> "#edc850";
            case 1024 -> "#edc53f";
            case 2048 -> "#edc22e";
            default -> "#3c3a32";
        };

        String foreground = value != null && value <= 4 ? "#776e65" : "#f9f6f2";

        return "-fx-background-color:" + background
                + ";-fx-text-fill:" + foreground
                + ";-fx-background-radius:6;-fx-font-weight:bold;";
    }
}