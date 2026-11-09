# Pidoku

Sudoku where every row, column and box holds the first nine digits of π (3 1 4 1 5 9 2 6 5)
instead of 1–9. So **1 and 5 appear twice**, and **7 and 8 never appear**.

Puzzles come from a custom engine that rates difficulty by the human solving techniques
they require (naked/hidden subsets, intersection removal, X-Wing, W-Wing), not by clue count.

## Modules

| Module | Stack | What it does |
|---|---|---|
| `pidoku-engine` | Java 21 | Generator, backtracking solver, strategy-based human solver, difficulty profiles |
| `pidoku-api` | Spring Boot 4, Postgres (Neon), Flyway | REST API for campaign, puzzle generation, progress |
| `pidoku-frontend` | React 19, TypeScript, Vite, Tailwind CSS 4, daisyUI 5 | The game |

See [docs/ROADMAP.md](docs/ROADMAP.md) for design decisions and what's planned next.

## Running locally

Requirements: JDK 21, Node 22+, and Docker (for API tests).

```bash
./gradlew build                 # builds and tests the engine and API (API tests use Testcontainers)
./gradlew :pidoku-api:bootRun   # runs the API
cd pidoku-frontend && npm install && npm run dev
cd pidoku-frontend && npm test  # frontend game-logic tests (Vitest)
```

The frontend currently runs fully offline. It plays puzzles bundled from
`pidoku-frontend/src/puzzles/*.json`, which are the `*-stats.json` files the engine's `Main` writes to
`output/`. Copy a new one in as `NN-difficulty.json` and it appears in the list. You can also load any
engine JSON file from the start screen. Progress is saved in localStorage.

The API reads its database settings from environment variables, or from a gitignored
`pidoku-api/.env` file in `.properties` format:

```properties
DATABASE_URL=jdbc:postgresql://<neon-pooled-host>/pidoku?sslmode=require
DATABASE_USER=...
DATABASE_PASSWORD=...
```

## Engine testing

Besides unit tests, `StrategySoundnessTest` is a property test: it runs every solving
strategy against thousands of random boards with known solutions. It fails if any
strategy places a wrong digit or eliminates a correct candidate.
