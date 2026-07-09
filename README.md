# 2048 REST Service

A Spring Boot REST API that exposes a 2048 game engine over HTTP. The project
demonstrates **hexagonal architecture (Ports & Adapters)**: the entire REST layer
was added without changing a single line of the game domain. The same `Board`,
`Game`, `Direction`, and expectimax AI that previously drove console, Swing, and
JavaFX interfaces now sit behind HTTP, unchanged.

## Highlights

- **Domain untouched.** The game rules (`domain/`) and the AI (`adapter/ai/`) are
  byte-for-byte the same as the desktop version, with their original unit tests
  still passing.
- **REST is purely additive.** A new inbound HTTP adapter plus one new port
  (`GameRepository`) is all it took.
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

## Project structure

```
com.pontalti.game2048
├── Game2048Application                 Spring Boot entry point
│
├── domain/                             core — framework-free, UNCHANGED
│   ├── Board  Game  Direction  GameStatus  Position
│   └── port/
│       ├── MoveAdvisor                 output port: "suggest a move"
│       └── GameRepository              output port: "store/find games by id"  (NEW)
│
└── adapter/
    ├── ai/                             AI advisor (implements MoveAdvisor)
    │   ├── ExpectimaxAdvisor           top-level MAX node + parallel coordinator
    │   └── ExpectedValue               one move's subtree (CHANCE node), a Callable
    │                                   run per legal direction on a virtual thread
    ├── persistence/
    │   └── InMemoryGameRepository      implements GameRepository (ConcurrentHashMap)
    └── rest/                           inbound HTTP adapter  (NEW)
        ├── GameController              the 5 public endpoints
        ├── TestSupportController       @Profile("test") only — seeds a board for tests
        ├── config/
        │   └── BeanConfig              declares beans, keeps the domain Spring-free
        ├── service/
        │   ├── GameService             application-service interface (+ CreatedGame record)
        │   └── GameServiceImpl         orchestrates repository + domain + advisor
        ├── exception/
        │   ├── GameExceptionHandler    @RestControllerAdvice -> HTTP status
        │   └── GameNotFoundException
        └── dto/
            ├── MoveRequest  GameResponse  HintResponse  ErrorResponse
            └── SeedGameRequest          test-only body for the seed endpoint
```

The dependency rule points inward: adapters depend on the domain; the domain
depends on nothing external. That is what allowed the same core to be driven by
four different interfaces over time.

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

**Stateless HTTP vs. stateful game.** Each request is independent, but a match
carries state. Every game gets a UUID and is stored server-side through the
`GameRepository` port; the client passes the id on each call. Swapping the
in-memory store for Redis or a database would touch only the persistence adapter —
not the domain or the REST layer.

**AI: expectimax split into two classes.** `ExpectimaxAdvisor` is the top-level
MAX node and parallel coordinator; for each legal direction it builds an
`ExpectedValue` task (that move's CHANCE node and deeper search) and evaluates the
four subtrees concurrently on virtual threads. Because `Board` is immutable, the
tasks share no mutable state and need no synchronization. The search is
deterministic — the expectation is computed analytically (no random sampling), and
ties are broken by direction order — so the same board always yields the same
suggestion.

**Interface + implementation for the service.** `GameService` is an interface and
`GameServiceImpl` the implementation, so the controller depends on the contract,
not the concrete class. The small `CreatedGame` record is declared on the
interface (part of the contract, letting `newGame()` return an id and a game
together), not on the implementation.

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
  same `GameRepository` port, with no change to the domain or the REST layer.
