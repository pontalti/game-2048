package com.pontalti.game2048;

import com.pontalti.game2048.adapter.ai.ExpectimaxAdvisor;
import com.pontalti.game2048.adapter.ui.console.ConsoleUI;
import com.pontalti.game2048.adapter.ui.fx.FxUI;
import com.pontalti.game2048.adapter.ui.swing.SwingUI;
import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.port.out.MoveAdvisor;

import javafx.application.Application;

import javax.swing.SwingUtilities;
import java.util.Random;
import java.util.Scanner;

/**
 * Composition root and mode selector.
 * <p>
 * Wires the domain ({@link Game}) and the AI advisor
 * ({@link ExpectimaxAdvisor} behind the {@link MoveAdvisor} port) to one of the
 * three UI adapters, chosen by the first command-line argument:
 * <ul>
 * <li>{@code console} — runs the text {@link ConsoleUI} in a game loop;</li>
 * <li>{@code fx} or {@code javafx} — launches the {@link FxUI} JavaFX GUI;</li>
 * <li>anything else, or no argument — launches the {@link SwingUI} desktop GUI.</li>
 * </ul>
 * All three adapters drive the same domain; only the input model differs.
 */
public class Main {

    public static void main(String... args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "swing";
        switch (mode) {
            case "console"      -> runConsole();
            case "fx", "javafx" -> runFx(args);
            default             -> runSwing();
        }
    }

    /**
     * Starts the text-based version of the game.
     * <p>
     * This method creates the required {@link Game} and move-advisor instances,
     * configures the console user interface, and starts its main input loop.
     * <p>
     * The console interface owns the lifecycle of {@code System.in}. It is
     * responsible for reading the player's commands and closing the input resource
     * when the console session ends.
     * <p>
     * This method is intended only for launching the console adapter. The game rules
     * remain inside the domain classes and are not implemented here.
     */
    private static void runConsole() {
        Random random = new Random();
        Game game = new Game(random);
        MoveAdvisor advisor = new ExpectimaxAdvisor();
        try (Scanner scanner = new Scanner(System.in)) {
            // The same Random is handed to the adapter so that the "new game"
            // command can build a fresh match without creating its own source of
            // randomness — mirroring how SwingUI is wired.
            new ConsoleUI(game, advisor, random, scanner).run();
        }
    }

    /**
     * Starts the Swing desktop version of the game.
     * <p>
     * This method creates the required {@link Game} and move-advisor instances,
     * initializes the Swing user interface, and displays the main game window.
     * <p>
     * Swing components must be created and updated on the Event Dispatch Thread
     * (EDT). Therefore, the interface is launched using
     * {@link javax.swing.SwingUtilities#invokeLater(Runnable)}.
     * <p>
     * Running the interface on the EDT ensures that window creation, keyboard
     * events, and visual updates are handled safely and in the correct order.
     * <p>
     * This method is responsible only for starting the Swing adapter. The game rules
     * remain inside the domain classes and are not implemented here.
     */
    private static void runSwing() {
        Random random = new Random();
        Game game = new Game(random);
        MoveAdvisor advisor = new ExpectimaxAdvisor();
        SwingUtilities.invokeLater(() -> new SwingUI(game, advisor, random).launch());
    }

    /**
     * Starts the JavaFX desktop version of the game.
     * <p>
     * Because {@code Main} does not extend {@link Application}, calling
     * {@link Application#launch(Class, String...)} here bypasses the JavaFX
     * runtime-check that otherwise fails with "JavaFX runtime components are
     * missing" when the modules are on the classpath (as in a fat jar) rather
     * than the module path.
     * <p>
     * The {@link FxUI} builds its own {@link Game} and move-advisor
     * instances internally, so none are created here. The game rules remain
     * inside the domain classes and are not implemented here.
     *
     * @param args the original command-line arguments, forwarded to JavaFX
     */
    private static void runFx(String[] args) {
        Application.launch(FxUI.class, args);
    }
}