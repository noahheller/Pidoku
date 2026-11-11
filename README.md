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

### Generator job

Puzzles are generated ahead of time and stored, not generated per request. The generator job
tops up the random pool (`pidoku.generator.pool-size-per-difficulty` puzzles of each difficulty)
and fills the daily puzzles for the next `pidoku.generator.daily-days-ahead` days. It's the same
application run with the `generator` profile: no web server, it does its work and exits.

```bash
./gradlew :pidoku-api:bootRun --args='--spring.profiles.active=generator'
```

Settings live in `pidoku-api/src/main/resources/application.properties`, including the
difficulties of each day's daily puzzles (`pidoku.daily.difficulties`, easy, medium and hard).

### Database migrations

Flyway owns the schema. Migrations live in `pidoku-api/src/main/resources/db/migration` and run
when the application starts. Hibernate only checks that the entities match the schema
(`ddl-auto=validate`), so a schema change always needs a migration.

- **Never edit a migration that has run on a shared database.** Flyway stores a checksum of each
  migration it applies, and refuses to start if a file changed afterwards. To change the schema,
  add the next version instead, e.g. `V3__create_players.sql`, even for a one-line fix.
- Don't use `flyway repair` to get past a checksum mismatch. It only updates the stored checksum,
  so the database keeps the old schema while Flyway believes it has the new one.
- The tests run every migration against a fresh Postgres in Docker, so `./gradlew build` catches a
  migration that doesn't apply or doesn't match the entities.

### Neon dev branch

A Neon branch is a cheap, copy-on-write copy of a database. Develop against a `dev` branch, and
keep the `main` branch for the deployed app, so trying out migrations locally can't break
production.

1. In the Neon console, open the project, go to **Branches → New branch**, name it `dev`, and
   choose `main` as its parent.
2. Open **Connect** on the `dev` branch and copy the **pooled** connection string. It looks like
   `postgresql://<user>:<password>@<host>-pooler.<region>.aws.neon.tech/<db>?sslmode=require`.
3. Put it in `pidoku-api/.env`, split into a JDBC URL (no credentials) plus the user and password:

   ```properties
   DATABASE_URL=jdbc:postgresql://<host>-pooler.<region>.aws.neon.tech/<db>?sslmode=require
   DATABASE_USER=<user>
   DATABASE_PASSWORD=<password>
   ```

To start over on `dev`, for example after a migration you were still editing has run there, use
**Reset from parent** on the branch. It goes back to a copy of `main`, and the next startup
re-applies your migrations. Run the generator job afterwards if you need puzzles in it.

## API

| Endpoint | Returns |
|---|---|
| `GET /api/puzzles/random?difficulty=hard` | A random puzzle from the pool |
| `GET /api/puzzles/daily` | Today's (UTC) date and its daily puzzles (easy, medium and hard), easiest first |
| `GET /api/puzzles/{shareCode}` | The puzzle behind a share code (case-insensitive) |
| `GET /actuator/health` | Health check |

Difficulties are `trivial`, `relaxing`, `easy`, `medium`, `hard` and `fiendish`. Puzzles come back
with flat, row-major `givens` and `solution` arrays (0 is an empty cell), plus `score`,
`dependency` and `hardestStrategy`. Errors are [problem details](https://www.rfc-editor.org/rfc/rfc9457):
404 for an unknown share code, 503 when the generator job hasn't produced a puzzle yet, 400 for a
bad request.

## Engine testing

Besides unit tests, `StrategySoundnessTest` is a property test: it runs every solving
strategy against thousands of random boards with known solutions. It fails if any
strategy places a wrong digit or eliminates a correct candidate.
