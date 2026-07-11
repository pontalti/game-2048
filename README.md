# 2048

A Java implementation of the classic [2048](https://play2048.co) sliding-tile
puzzle, with three interchangeable user interfaces (Swing, JavaFX, and a text
console) and an offline AI advisor that suggests the best move.

The project is built around a **hexagonal (Ports & Adapters)** architecture: the
game rules live in a self-contained domain that knows nothing about the UI or the
AI, and each interface is just an adapter plugged into the same core.

---

## Requirements coverage

| # | Requirement | Where |
|---|-------------|-------|
| 1 | Generate an initial board | `Game` constructor loops `Board.spawnRandom`, count from `InitialGameTileLimit` |
| 2 | Move Left | `Board.move(Direction.LEFT)` |
| 3 | Move Right | `Board.move(Direction.RIGHT)` |
| 4 | Move Up / Down | `Board.move(Direction.UP / DOWN)` |
| 5 | Spawn a 2/4 after a valid move | `Game.play` -> `Board.spawnRandom` |
| 6 | Win / Lose detection | `Board.hasWon` / `Board.isGameOver` |
| 6 | AI move suggestion | `ExpectimaxAdvisor` (behind the `MoveAdvisor` output port) |

---

## Prerequisites

- **JDK 25** (the project is compiled for release 25).
- **Maven 3.9+**.

JavaFX is resolved automatically as a Maven dependency. Because JavaFX ships
native libraries that differ per operating system and CPU architecture, the build
uses a **Maven profile to select the right ones**. You choose the profile that
matches your system with the `-P` flag, as shown below.

---

## Build

Select the profile for your platform and build:

```bash
mvn clean package -P <profile>
```

Replace `<profile>` with the one matching your operating system:

| OS / architecture | Profile | JavaFX natives bundled |
|-------------------|---------|------------------------|
| Linux (x86_64) | `fx-linux` | `linux` |
| Windows (x86_64) | `fx-windows` | `win` |
| macOS Intel (x86_64) | `fx-mac-intel` | `mac` |
| macOS Apple Silicon (aarch64) | `fx-mac-arm` | `mac-aarch64` |

For example, on Linux:

```bash
mvn clean package -P fx-linux
```

This compiles the code, runs the full test suite, and produces a self-contained
executable jar at `target/game2048.jar` with the JavaFX natives for the selected
profile bundled in.

To skip the tests during a build, add `-DskipTests`:

```bash
mvn clean package -P fx-linux -DskipTests
```

---

## Run

The jar has a single entry point that selects the interface from the first
argument. **Swing is the default.** The run commands are identical across
operating systems:

```bash
# Swing desktop GUI (default)
java -jar target/game2048.jar

# Text console
java -jar target/game2048.jar console

# JavaFX desktop GUI
java -jar target/game2048.jar fx
```

The JavaFX GUI can also be launched directly through Maven, which runs it on the
module path (and avoids the fat-jar warning described
[below](#note-on-the-javafx-fat-jar-warning)):

```bash
mvn javafx:run -P <profile>
```

### Controls

| Interface | Move | Hint | New game | Quit |
|-----------|------|------|----------|------|
| Swing / JavaFX | Arrow keys or W/A/S/D | `H` | `N` | close window |
| Console | W/A/S/D + Enter | `H` | -- | `Q` |

---

## Building and running per operating system

Only the build profile changes between systems; the `java -jar` run commands are
the same everywhere.

### Linux (x86_64)

```bash
mvn clean package -P fx-linux
java -jar target/game2048.jar          # Swing
java -jar target/game2048.jar console  # Console
java -jar target/game2048.jar fx       # JavaFX
mvn javafx:run -P fx-linux             # JavaFX via module path
```

### Windows (x86_64)

```powershell
mvn clean package -P fx-windows
java -jar target/game2048.jar          # Swing
java -jar target/game2048.jar console  # Console
java -jar target/game2048.jar fx       # JavaFX
mvn javafx:run -P fx-windows           # JavaFX via module path
```

### macOS -- Intel (x86_64)

```bash
mvn clean package -P fx-mac-intel
java -jar target/game2048.jar          # Swing
java -jar target/game2048.jar console  # Console
java -jar target/game2048.jar fx       # JavaFX
mvn javafx:run -P fx-mac-intel         # JavaFX via module path
```

### macOS -- Apple Silicon (M1/M2/M3/M4, aarch64)

```bash
mvn clean package -P fx-mac-arm
java -jar target/game2048.jar          # Swing
java -jar target/game2048.jar console  # Console
java -jar target/game2048.jar fx       # JavaFX
mvn javafx:run -P fx-mac-arm           # JavaFX via module path
```

### How the profile works

The `pom.xml` declares four profiles, one per platform. The selected profile sets
the `javafx.platform` property, which becomes the `<classifier>` of the
`javafx-controls` dependency — that classifier is what pulls in the correct native
libraries.

To list the profiles available in the project:

```bash
mvn help:all-profiles
```

> **Note:** the produced `game2048.jar` contains the natives of the profile it was
> built with, so it is not portable to a different OS. This is intentional for
> this project -- each user builds on their own machine before running.

---

## Test

Run the test suite (pick any valid profile so JavaFX resolves):

```bash
mvn test -P fx-linux
```

The suite covers the domain and the AI:

- **`BoardTest`** -- the four moves, the single-merge-per-move rule, win/lose
  detection, immutability, and defensive copying. The move and endgame cases use
  the exact board examples from the problem statement.
- **`GameTest`** -- status evaluation, the valid-move flow (move -> spawn ->
  re-evaluate), invalid-move handling, and terminal-state guards. A fixed-seed
  `Random` makes every test reproducible.
- **`ExpectimaxAdvisorTest`** -- the advisor only ever returns a legal move,
  returns empty at a dead end, is deterministic, and validates its depth argument.

---

## The AI advisor

The move suggestion (requirement 6) is an **offline expectimax** search -- no
network, no external service, no credentials. Expectimax fits 2048 because the
"opponent" is not adversarial: after each move a tile spawns *randomly*. Rather
than assuming the worst placement (as minimax would), the search alternates:

- **MAX nodes** -- the player picks the direction with the best expected value;
- **CHANCE nodes** -- the spawn is modeled as a probability-weighted average over
  every empty cell receiving a 2 (90%) or a 4 (10%).

The search is depth-limited; leaf boards are scored by a heuristic combining free
space, a "snake" positional gradient (which keeps large tiles ordered toward a
corner), and smoothness (adjacent tiles close in value merge more easily). The
search itself uses no randomness, so a given board always yields the same
suggestion.

The advisor is split across two classes. `ExpectimaxAdvisor` is the top-level MAX
node and coordinator: for each legal direction it builds an `ExpectedValue` task
and evaluates the four subtrees concurrently on virtual threads. `ExpectedValue`
holds one move's subtree -- the CHANCE node, the deeper search, and the leaf
heuristic -- as a `Callable`. Because `Board` is immutable, the parallel tasks
share no mutable state and need no synchronization; and because results are
compared in a fixed direction order (replacing only on a strictly greater score),
the parallel search stays deterministic.

---

## Architecture

```
domain/                  pure game rules -- no I/O, no UI, no framework
  Board                  immutable 4x4 board; the four moves derive from "move left"
  Direction, Position    value objects
  Game, GameStatus       orchestrates a match (move -> spawn -> evaluate)
  InitialGameTileLimit   enum (MIN/MAX) parameterizing the initial tile count
  port/out/MoveAdvisor   output port: "suggest a move" without knowing how

adapter/
  ai/ExpectimaxAdvisor   offline AI implementing MoveAdvisor (MAX node + coordinator)
  ai/ExpectedValue       one move's subtree (CHANCE node), evaluated in parallel
  ui/console/ConsoleUI   text adapter (blocking game loop)
  ui/swing/SwingUI       Swing adapter (event-driven, Key Bindings)
  ui/fx/FxUI             JavaFX adapter (event-driven, scene key handler)

Main                     composition root; selects the UI by argument
```

The domain has zero dependencies on the adapters. Every interface translates its
own input (a keystroke or a typed character) into a `Direction` and calls
`Game.play(Direction)` -- which is why the three UIs are nearly identical and why
swapping or adding one requires no change to the game rules or the tests.

### Ports: only an output port here

This desktop version has a single port, and it is an **output** (driven) port:
`MoveAdvisor`, in `domain/port/out/`. There is deliberately **no input port**.
The UIs and the domain share one process and one memory space, so an interface
drives the game by holding a `Game` and calling its public API (`play`,
`getBoard`, `getStatus`) directly -- the aggregate's own API *is* the entry
boundary. An explicit input port earns its place only when there is a delivery
boundary to cross (a network, a stateless protocol). The REST branch of this
project adds exactly that: a `GamePort` input port and a `GameRepository` output
port, because there the client is out of process and game state must be stored
between stateless requests. The shared core -- rules and AI -- is identical in
both branches; only the ports differ.

---

## Assumptions

The problem statement invites reasonable assumptions; these are the ones made
here:

- **Board size** is fixed at 4x4 (matching every example in the statement).
- A **new tile is a 2 with 90% probability and a 4 with 10%**, following the
  original 2048. (The statement guarantees at least a 2; the 4 is a reasonable
  extension it explicitly allows.)
- A **fresh game starts with two randomly placed tiles**. The count is
  parameterized by the `InitialGameTileLimit` enum (`MIN`..`MAX`, both currently
  2), so it lives in a single place and is trivial to widen without touching the
  game logic. The statement says "a random number of `2`s"; two is a deliberate,
  stated reading of that, and equal bounds make it deterministic.
- Empty cells are represented as `null` (mirroring the `null` used in the
  statement's JSON), not `0`.
- The game **stops at a win** (reaching 2048); there is no "keep going" mode.
- A **new tile spawns only after a move that actually changes the board**; an
  invalid move (one that changes nothing) neither spawns a tile nor advances the
  game.
- The AI advisor searches to a **default depth of 3 player moves**, which plays
  strongly while remaining fast; the depth is configurable via the
  `ExpectimaxAdvisor(int)` constructor.

---

## Note on the JavaFX fat-jar warning

Running the JavaFX mode from the executable jar prints:

```
WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module'
```

This is expected when JavaFX is loaded from the classpath (a fat jar) rather than
the module path -- the game runs normally. To run the JavaFX GUI without the
warning, use `mvn javafx:run`, which launches it on the module path.
