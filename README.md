# 2048 REST Service

A Spring Boot REST API that exposes a 2048 game engine over HTTP. The project
demonstrates **hexagonal architecture (Ports & Adapters)**: the entire REST layer
was added without changing a single line of the game domain. The same `Board`,
`Game`, `Direction`, and expectimax AI that previously drove console, Swing, and
JavaFX interfaces now sit behind HTTP — and a browser UI plays through that same
API, unchanged core throughout.

## Highlights

- **Domain untouched.** The game rules (`domain/`) and the AI (`adapter/ai/`) are
  byte-for-byte the same as the desktop version, with their original unit tests
  still passing.
- **Symmetric ports.** Both sides of the hexagon are modelled explicitly: an
  input port (`port/in`) for driving the game, and output ports (`port/out`) for
  persistence and the AI — all owned by the domain.
- **REST + browser UI, both additive.** A REST adapter and a separate web client
  were added on top of the same core; neither required domain changes.
- **Full API with Swagger / OpenAPI 3.1.**
- **End-to-end tests** that drive win, loss, and hint entirely over HTTP.

## Tech stack

- Java 25
- Spring Boot 4.1.0 (starter-web, -validation, -webmvc-test)
- springdoc-openapi 3.0.3 (Swagger UI)
- Maven (bundled `./mvnw` wrapper)

> **Version note.** Spring Boot and springdoc must stay on the same generation.
> This project pairs Boot 4.x with springdoc 3.x. (Boot 3.x would pair with
> springdoc 2.x.) Mixing generations puts two sets of auto-configuration on the
> classpath and fails at startup with a duplicate-bean error.

## Ports & Adapters at a glance

The domain owns every port; adapters plug into them from outside. Dependencies
always point inward, toward the domain.

```
   inbound adapters            input port         application        output ports          outbound adapters

   HTTP client ─▶ GameController ─▶ [ port/in ] ─▶ GameServiceImpl ─▶ [ port/out ] ─▶ InMemoryGameRepository
   Browser UI  ─▶ (REST)            GamePort                          GameRepository
                                                                      MoveAdvisor    ─▶ ExpectimaxAdvisor
```

- **Input port** (`domain/port/in/GamePort`) — the use cases the app offers.
  Inbound adapters (the REST controller; the browser UI via HTTP) depend on this
  interface, never on the implementation.
- **Output ports** (`domain/port/out/`) — `GameRepository` (persistence) and
  `MoveAdvisor` (AI). The domain declares them; adapters implement them.
- **`GameServiceImpl`** implements the input port and orchestrates the output
  ports with the domain — it holds no game rules.

## Project structure

```
com.pontalti.game2048
├── Game2048Application                 Spring Boot entry point
│
├── domain/                             core — framework-free, UNCHANGED
│   ├── Board  Game  Direction  GameStatus  Position
│   └── port/
│       ├── in/
│       │   └── GamePort                input port: the game use cases (+ CreatedGame)
│       └── out/
│           ├── GameRepository          output port: "store/find games by id"
│           └── MoveAdvisor             output port: "suggest a move"
│
└── adapter/
    ├── ai/                             AI advisor (implements MoveAdvisor)
    │   ├── ExpectimaxAdvisor           top-level MAX node + parallel coordinator
    │   └── ExpectedValue               one move's subtree (CHANCE node), a Callable
    │                                   run per legal direction on a virtual thread
    ├── persistence/
    │   └── InMemoryGameRepository      implements GameRepository (ConcurrentHashMap)
    └── rest/                           inbound HTTP adapter
        ├── controller/
        │   ├── GameController          the 5 public endpoints; depends on GamePort
        │   └── TestSupportController   @Profile("test") only — seeds a board for tests
        ├── config/
        │   ├── BeanConfig              declares beans, keeps the domain Spring-free
        │   └── WebConfig               CORS for the browser UI
        ├── service/
        │   └── GameServiceImpl         implements GamePort; orchestrates repo + domain + advisor
        ├── exception/
        │   ├── GameExceptionHandler    @RestControllerAdvice -> HTTP status
        │   └── GameNotFoundException
        └── dto/
            ├── MoveRequest  GameResponse  HintResponse  ErrorResponse
            └── SeedGameRequest          test-only body for the seed endpoint
```

## Prerequisites

- JDK 25+
- No local Maven required — use the `./mvnw` wrapper.

## Build & run

```bash
# run the tests
./mvnw clean test

# build the executable jar (target/game2048.jar)
./mvnw clean package

# run the service
./mvnw spring-boot:run
# or, after packaging:
java -jar target/game2048.jar
```

The service starts on `http://localhost:8080`.

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/games` | Create a new game (two starting tiles) |
| GET | `/games/{id}` | Get the current board and status |
| POST | `/games/{id}/moves` | Play a move; body `{"direction":"LEFT"}` |
| GET | `/games/{id}/hint` | AI-suggested move (does not change the game) |
| DELETE | `/games/{id}` | Delete a game |

- **Directions:** `LEFT`, `RIGHT`, `UP`, `DOWN`.
- **Status:** `PLAYING`, `WON`, `LOST`.
- **Board:** a 4x4 grid of numbers; `null` marks an empty cell (mirrors the
  problem-statement JSON).

### Swagger / OpenAPI

With the app running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Web UI (browser client)

A standalone React page plays the game in the browser by calling this API — it is
just another inbound adapter, talking to the domain purely over HTTP. Keyboard
controls: arrow keys or `W`/`A`/`S`/`D` to move, `H` for an AI hint.

Because the UI is served from a different origin than the API, CORS must be open
for it. `WebConfig` allows the common local origins (Vite `5173`, `3000`,
`http.server` `8000`, Live Server `127.0.0.1:5500`); adjust the list to match
wherever the UI is served.

Quick way to run it locally:

```bash
# 1) start the API
./mvnw spring-boot:run

# 2) serve the UI from a folder containing the HTML, from an allowed origin
python3 -m http.server 8000
# then open http://localhost:8000 and point the client at http://localhost:8080
```

Serve the page over HTTP (not opened as a `file://` path), so its origin matches
the CORS allow-list.

## Example session (curl)

```bash
# create a game and capture its id
ID=$(curl -s -X POST http://localhost:8080/games | jq -r .id)

# view the state
curl -s http://localhost:8080/games/$ID | jq

# ask the AI for a hint
curl -s http://localhost:8080/games/$ID/hint | jq

# play a move
curl -s -X POST http://localhost:8080/games/$ID/moves \
     -H 'Content-Type: application/json' \
     -d '{"direction":"LEFT"}' | jq

# delete the game
curl -s -X DELETE http://localhost:8080/games/$ID -o /dev/null -w "%{http_code}\n"
```

## Design notes

**Symmetric hexagon (in/out ports).** Every crossing of the domain boundary goes
through a port the domain owns: `GamePort` on the driving side, `GameRepository`
and `MoveAdvisor` on the driven side. Inbound adapters depend on the input port;
outbound adapters implement the output ports. The domain depends on none of them.

**Many front-ends, one core.** The same domain has been driven by console, Swing,
JavaFX, a REST API, and now a browser UI. Each is an inbound adapter; none changed
the game rules. The browser UI reaches the core through the REST adapter, so it is
an adapter on top of an adapter — still no domain coupling.

**Stateless HTTP vs. stateful game.** Each request is independent, but a match
carries state. Every game gets a UUID and is stored server-side through the
`GameRepository` output port; the client passes the id on each call. Swapping the
in-memory store for Redis or a database would touch only the persistence adapter.

**AI: expectimax split into two classes.** `ExpectimaxAdvisor` is the top-level
MAX node and parallel coordinator; for each legal direction it builds an
`ExpectedValue` task (that move's CHANCE node and deeper search) and evaluates the
four subtrees concurrently on virtual threads. Because `Board` is immutable, the
tasks share no mutable state and need no synchronization. The search is
deterministic — the expectation is computed analytically (no random sampling), and
ties are broken by direction order — so the same board always yields the same
suggestion.

**Concurrency.** `InMemoryGameRepository` is a singleton `@Repository` shared by
all request threads, so its map is a `ConcurrentHashMap` (safe concurrent access).
The map protects itself, not the `Game` objects inside it, so moves on the same
game are serialized with `synchronized (game)` in `GameServiceImpl`. Different
games lock on different instances and run fully in parallel.

**Domain stays framework-free.** No Spring annotations leak into `domain/`. Beans
(`MoveAdvisor`, `Random`) are declared in `config/BeanConfig`, which is precisely
what keeps the domain a pure, independently testable core.

## Testing

```bash
./mvnw test
```

The suite covers:

- **Domain** — `BoardTest`, `GameTest`: the exact examples from the problem
  statement (moves, spawn, endgame, immutability).
- **AI** — `ExpectimaxAdvisorTest`: the advisor never suggests an illegal move and
  returns empty only at a dead end.
- **Scenarios** — `GameScenarioTest`: win, loss, and hint driven purely over HTTP.
  A known board is planted via the test-only `POST /test/games` endpoint, which is
  guarded by `@Profile("test")` and therefore absent in production. A losing board
  is planted as an already-terminal position rather than reached by play, to keep
  the test deterministic — the random tile spawn would otherwise make it flaky.

## Notes

- The in-memory repository keeps games in a single server's memory; they are lost
  on restart and not shared across instances. That is intentional for this demo —
  making it durable or multi-instance means adding a Redis/JPA adapter behind the
  same `GameRepository` output port, with no change to the domain or the REST layer.
- `WebConfig` opens CORS for local development origins only. For a real deployment,
  restrict the allowed origins to the actual UI host.
