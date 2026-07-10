package com.pontalti.game2048.adapter.ui.swing;

import com.pontalti.game2048.domain.Board;
import com.pontalti.game2048.domain.Direction;
import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.GameStatus;
import com.pontalti.game2048.domain.port.out.MoveAdvisor;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Provides the Swing desktop interface for the 2048 game.
 * <p>
 * This class acts as an adapter between the graphical user interface and the
 * game domain. Like {@code ConsoleUI}, it controls the same {@link Game}
 * instance without changing or duplicating any game rules.
 * <p>
 * Swing applications are event-driven, so this adapter does not use a
 * traditional game loop. Instead, each keyboard event is captured by a key
 * binding, converted into a {@link Direction}, and passed to
 * {@link Game#play(Direction)}.
 * <p>
 * The keyboard-to-direction mapping is defined only in this class. This keeps
 * user-interface concerns separate from the domain logic and allows the game
 * rules to remain independent of Swing.
 * <p>
 * Swing is included in the JDK through the {@code java.desktop} module and is
 * implemented entirely in Java. Therefore, the application does not require
 * additional native libraries for each operating system and can be packaged as
 * a single cross-platform executable JAR.
 * <p>
 * The user interface is created and updated on the Swing Event Dispatch Thread
 * (EDT) through {@link #launch}. Keyboard event handlers also run on the EDT, so
 * the single-threaded {@link Game} instance can be accessed safely without
 * additional synchronization.
 */
public final class SwingUI {

    private Game game;
    private final MoveAdvisor advisor;
    private final Random random;

    private final JLabel[][] cells = new JLabel[Board.SIZE][Board.SIZE];
    private final JLabel statusLabel = new JLabel(" ");

    public SwingUI(Game game, MoveAdvisor advisor, Random random) {
        this.game = Objects.requireNonNull(game, "game cannot be null");
        this.advisor = Objects.requireNonNull(advisor, "advisor cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    /**
     * Creates, configures, and displays the main game window.
     * <p>
     * This method is the entry point for starting the Swing user interface. It
     * initializes the window, creates the visual components, registers the keyboard
     * controls, and renders the current game state.
     * <p>
     * Swing components must be created and updated on the Event Dispatch Thread
     * (EDT). Therefore, this method should be called from {@code main} using
     * {@link SwingUtilities#invokeLater(Runnable)}.
     * <p>
     * Running the interface on the EDT ensures that window creation, user input,
     * and visual updates are processed safely and in the correct order.
     */
    public void launch() {
        applySystemLookAndFeel();
        JFrame frame = new JFrame("2048");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(false);

        JPanel boardPanel = new JPanel(new GridLayout(Board.SIZE, Board.SIZE, 8, 8));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        boardPanel.setBackground(new Color(0xbbada0));

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                JLabel cell = new JLabel("", SwingConstants.CENTER);
                cell.setOpaque(true);
                cell.setPreferredSize(new Dimension(80, 80));
                cell.setFont(new Font("SansSerif", Font.BOLD, 26));
                cells[r][c] = cell;
                boardPanel.add(cell);
            }
        }

        JButton hintButton = new JButton("Hint (H)");
        hintButton.setFocusable(false);
        hintButton.addActionListener(e -> showHint());

        JButton newGameButton = new JButton("New Game (N)");
        newGameButton.setFocusable(false); // don't let it capture the arrow keys
        newGameButton.addActionListener(e -> newGame());

        // Two buttons side by side, centered as a row.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.add(hintButton);
        buttons.add(newGameButton);

        JLabel help = new JLabel("Arrows or WASD to move  ·  H for a hint  ·  N for new game",
                SwingConstants.CENTER);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        help.setAlignmentX(Component.CENTER_ALIGNMENT);

        south.add(buttons);                        // era: south.add(hintButton);
        south.add(Box.createVerticalStrut(8));
        south.add(statusLabel);
        south.add(Box.createVerticalStrut(4));
        south.add(help);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(boardPanel, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        registerKeyBindings(root);

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null); // center on screen
        frame.setVisible(true);

        render();
    }

    /**
     * Registers the keyboard commands used to control the game.
     * <p>
     * The arrow keys and the W, A, S, and D keys are mapped to their corresponding
     * movement directions. The H key is mapped to the command that displays a move
     * suggestion.
     * <p>
     * This method uses the Swing Key Bindings API instead of a {@code KeyListener}.
     * Key bindings are more reliable because they do not require the component to
     * hold direct keyboard focus and are less likely to be intercepted by other
     * interface elements, such as buttons.
     * <p>
     * The bindings are registered on the provided component and remain active while
     * its window is focused.
     *
     * @param component the Swing component on which the keyboard bindings are
     * registered
     */
    private void registerKeyBindings(JComponent component) {
        bindMove(component, "UP",    Direction.UP,    "UP", "W");
        bindMove(component, "DOWN",  Direction.DOWN,  "DOWN", "S");
        bindMove(component, "LEFT",  Direction.LEFT,  "LEFT", "A");
        bindMove(component, "RIGHT", Direction.RIGHT, "RIGHT", "D");

        InputMap in = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap act = component.getActionMap();

        in.put(KeyStroke.getKeyStroke("H"), "hint");
        act.put("hint", action(e -> showHint()));

        in.put(KeyStroke.getKeyStroke("N"), "newGame");
        act.put("newGame", action(e -> newGame()));
    }

    /**
     * Associates one or more keyboard shortcuts with a single movement action.
     * <p>
     * Each keystroke provided in {@code keys} is registered under the same action
     * name and mapped to the specified {@link Direction}. When the player presses
     * any of those keys, the corresponding move is sent to the game.
     * <p>
     * This allows different keys, such as an arrow key and its equivalent WASD key,
     * to perform the same movement without duplicating the action logic.
     *
     * @param component the Swing component on which the key bindings are registered
     * @param name the unique name used to identify the movement action
     * @param dir the direction associated with the action
     * @param keys one or more keystroke descriptions that trigger the same move
     */
    private void bindMove(JComponent component, String name, Direction dir, String... keys) {
        InputMap in = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap act = component.getActionMap();
        for (String key : keys) {
            in.put(KeyStroke.getKeyStroke(key), name);
        }
        act.put(name, action(e -> play(dir)));
    }

    /**
     * Applies the specified move and refreshes the user interface.
     * <p>
     * Before processing the move, this method verifies that the game is still in a
     * playable state. If the game has already ended, the requested direction is
     * ignored.
     * <p>
     * When the game is running, the direction is passed to
     * {@link Game#play(Direction)} so that the domain logic can update the board,
     * generate a new tile when appropriate, and re-evaluate the game status.
     * <p>
     * After the move is processed, the interface is repainted to display the latest
     * board state and status information.
     *
     * @param dir the direction of the move requested by the player
     */
    private void play(Direction dir) {
        if (game.getStatus() == GameStatus.PLAYING) {
            game.play(dir);
            render();
        }
    }

    /**
     * Requests a move suggestion from the configured advisor and displays it to the
     * player.
     * <p>
     * The advisor analyzes the current board and returns the best available
     * direction based on its evaluation strategy.
     * <p>
     * This method does not perform the suggested move and does not modify the board
     * or any other part of the game state. It only presents the recommendation in
     * the user interface.
     * <p>
     * If no valid move is available, the method displays an appropriate message
     * indicating that the board has reached a dead end.
     */
    private void showHint() {
        Optional<Direction> suggestion = advisor.suggest(game.getBoard());
        statusLabel.setText(suggestion
                .map(dir -> "Hint: move " + dir)
                .orElse("Hint: no move changes the board"));
    }

    /**
     * Refreshes the graphical interface using the current game state.
     * <p>
     * This method reads the latest board and redraws every cell in its corresponding
     * row and column. Cells containing tiles are updated to display their current
     * values, while empty cells are rendered without a value.
     * <p>
     * It also updates the status line with the latest game information, such as the
     * current score, game status, or other messages shown to the player.
     * <p>
     * This method only updates the visual representation of the game. It does not
     * perform moves or modify the board or any other part of the domain state.
     */
    private void render() {
        Integer[][] grid = game.getBoard().toArray();
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Integer v = grid[r][c];
                JLabel cell = cells[r][c];
                cell.setText(v == null ? "" : String.valueOf(v));
                cell.setBackground(backgroundFor(v));
                cell.setForeground(v != null && v <= 4 ? new Color(0x776e65)
                        : new Color(0xf9f6f2));
            }
        }
        switch (game.getStatus()) {
            case WON     -> statusLabel.setText("You win!");
            case LOST    -> statusLabel.setText("Game over — no moves left");
            case PLAYING -> { /* keep whatever is there (may be a hint) */ }
        }
    }

    /**
     * Returns the background color associated with the specified tile value.
     * <p>
     * This method defines the visual color palette used by the game. Each tile
     * value is mapped to a different color, following a style similar to the
     * original 2048 game.
     * <p>
     * Lower-value tiles usually use lighter colors, while higher-value tiles use
     * stronger or darker colors to make them easier to distinguish.
     * <p>
     * Empty cells are assigned a default background color.
     *
     * @param v the value of the tile, or the value representing an empty cell
     * @return the background color associated with the specified tile value
     */
    private static Color backgroundFor(Integer v) {
        int code = switch (v == null ? 0 : v) {
            case 0    -> 0xcdc1b4;
            case 2    -> 0xeee4da;
            case 4    -> 0xede0c8;
            case 8    -> 0xf2b179;
            case 16   -> 0xf59563;
            case 32   -> 0xf67c5f;
            case 64   -> 0xf65e3b;
            case 128  -> 0xedcf72;
            case 256  -> 0xedcc61;
            case 512  -> 0xedc850;
            case 1024 -> 0xedc53f;
            case 2048 -> 0xedc22e;
            default   -> 0x3c3a32;
        };
        return new Color(code);
    }

    /**
     * Creates a Swing {@link Action} whose behavior is defined by the provided
     * lambda expression.
     * <p>
     * This helper reduces the amount of boilerplate required when creating an
     * {@link javax.swing.AbstractAction}. When the action is triggered, the received
     * {@link java.awt.event.ActionEvent} is passed to the specified
     * {@link java.util.function.Consumer}.
     * <p>
     * It is mainly used when registering keyboard bindings, allowing the action
     * logic to be written as a concise lambda instead of creating a separate
     * anonymous class for each command.
     *
     * @param body the function that processes the action event when the action is
     * triggered
     * @return a Swing action that delegates its execution to the provided function
     */
    private static Action action(java.util.function.Consumer<java.awt.event.ActionEvent> body) {
        return new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                body.accept(e);
            }
        };
    }

    /**
     * Starts a new game and refreshes the user interface.
     * <p>
     * This method creates a new {@link Game} instance, replacing the current game
     * with a fresh initial state.
     * <p>
     * After the new game is created, the interface is repainted so that the board,
     * score, and status information reflect the new session.
     * <p>
     * Only the adapter state is updated. The domain implementation remains
     * unchanged, and no game rules are duplicated or modified by this method.
     */
    private void newGame() {
        this.game = new Game(random);
        render();
    }

    /**
     * Applies the operating system's native look and feel to the Swing user
     * interface.
     * <p>
     * This allows the application window and its components to visually match the
     * desktop environment on which the game is running. For example, Swing may use
     * the GTK look and feel on Linux or the corresponding native appearance on
     * Windows and macOS.
     * <p>
     * Applying the system look and feel is a best-effort operation. If the native
     * look and feel is unavailable or cannot be loaded, the application continues
     * using Swing's default cross-platform appearance.
     * <p>
     * A failure to apply the native style does not affect the game functionality.
     */
    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore: the default look-and-feel works everywhere.
        }
    }
}