# 🎮 2048

2048 - code challenge.

---

## 🧭 Project Branches

This repository contains **two implementations** of the same game, built on top of an
**identical business domain**:

- **Desktop**: branch
  [`DESKTOP_VERSION`](https://github.com/pontalti/game-2048/tree/DESKTOP_VERSION)
  The deliverable. A plain Java application with three interchangeable interfaces —
  console, Swing and JavaFX — and no framework.

- **REST**: branch
  [`REST_VERSION`](https://github.com/pontalti/game-2048/tree/REST_VERSION)
  The same game exposed over HTTP with Spring Boot, plus a browser client.

To switch between versions:

```bash
# Desktop
git checkout DESKTOP_VERSION

# REST
git checkout REST_VERSION
```

Each branch carries its own `README.md` with build, run and design details.

---

## 🎯 The point of having two

The challenge asked for a well-structured, maintainable codebase. Rather than only
claiming the architecture delivers that, the second branch **proves** it.

Both branches share the **same domain, unchanged** — the game rules and the AI were
not adapted, ported or rewritten to reach the web. These files are byte-for-byte
identical on both branches:

```
domain/Board                domain/Game               domain/Direction
domain/GameStatus           domain/Position           domain/MoveResult
domain/Collapsed            domain/MergedRow          domain/Snapshot
domain/InitialGameTileLimit                 domain/port/out/MoveAdvisor
adapter/ai/ExpectimaxAdvisor                adapter/ai/ExpectedValue
```

Moving from a desktop window to an HTTP API touched only the **adapters**. The rules
of 2048 never learned that the interface had changed.

That is hexagonal architecture (Ports & Adapters) doing its job: the domain sits at
the centre, knows nothing about the outside world, and every delivery technology
plugs into it from the edge.

---

## 🔌 Same core, different boundaries

The two branches are not copies of each other. Each declares the ports its own
boundary actually requires — a port exists where a real boundary exists, not for its
own sake.

| | Desktop | REST |
|---|---|---|
| **Domain + AI** | identical | identical |
| **Inbound adapters** | console, Swing, JavaFX | HTTP controller, browser UI |
| **Input port** | none needed | `GamePort` |
| **Output ports** | `MoveAdvisor` | `MoveAdvisor`, `GameRepository` |
| **State** | in memory, for as long as the window is open | stored per game id, server-side |

**Why the desktop needs no input port:** the UI and the domain share one process and
one memory space, so the UI holds the `Game` object and drives it directly — the
aggregate's public API *is* the entry boundary.

**Why REST needs one:** there is a network to cross and HTTP is stateless, so the use
cases must be declared explicitly (`GamePort`) and each match has to be stored and
retrieved by id (`GameRepository`).

---

## 🕹️ The game

Both branches play the full game and behave identically, because they share the same
domain:

- the four moves, tile spawning (90% a `2`, 10% a `4`), win at `2048`, loss on a dead
  end;
- an **offline AI advisor** (expectimax) that suggests the best move — no network, no
  credentials;
- a **score** following the original rule: each merge awards the value of the tile it
  produces;
- **undo**, rolling a move back and restoring board, score and status together.

Not one line of that logic lives in a UI or in a controller.
