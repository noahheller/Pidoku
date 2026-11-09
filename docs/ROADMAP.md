# Pidoku Roadmap

Pidoku is Sudoku using the first nine digits of π: every row, column and box holds
3 1 4 1 5 9 2 6 5, so 1 and 5 appear twice and 7, 8 and 0 never appear.
It's a resume project and a game to play with friends.

## Decisions made

### Stack
- Java 21 engine and Spring Boot API in one Gradle build.
- React 19 + TypeScript + Vite frontend.
- Hosting: Cloud Run (scales to zero), Neon Postgres and Firebase Hosting.

### Puzzles are generated ahead of time
Campaign, daily and random puzzles are made by a background job and stored in the database,
not generated during a request (Hard and Fiendish take about 4–11 seconds). Stored puzzles
also keep share codes and campaign levels stable when the engine changes. Every puzzle is
stamped with an `engine_version`.

The one exception is **custom generation** (milestone 4), where the player designs their
own difficulty and the puzzle has to be generated on demand.

### How difficulty is scored
- At each step the solver takes the easiest available move.
- "Open moves" is Pelánek's dependency metric: the average number of cells fillable with a
  single over the first 25 steps. The reference is `min(15, empty cells at start ÷ 2)`, a
  tweak so near-full boards aren't penalised.
- All weights are hand-picked and meant to be fitted later to real solve times. Pelánek's
  research supports the ingredients (hardest step plus summed difficulty plus open moves),
  but not these exact numbers.

### Daily puzzle rules
- "Today" is the **UTC** date. The daily changes at 00:00 UTC for everyone, a solve counts
  toward the leaderboard of the UTC date its puzzle belongs to, and streaks count
  consecutive UTC dates.
- The number in "Pidoku #123" is days since launch (launch day is #1).

## Milestones

1. Schema, generator job and read endpoints, tested locally against Testcontainers.
2. Deploy early: Cloud Run, the nightly generator job, Neon and Firebase with CI/CD, even
   with only the daily and random endpoints.
3. Anonymous sign-in and players, so everything after this knows who the player is.
4. Custom generation: players build their own difficulty.
5. Campaign mode, end to end.
6. Attempts, leaderboards, Google sign-in and friends.
7. Hints and share codes.
8. Calibrate difficulty once real data exists.

## 1. Database, generator job and API

### Schema (Flyway migrations in `pidoku-api`)
| Table | Columns |
|---|---|
| `puzzles` | id, share_code, source, size, givens and solution (81-character strings), clue_count, difficulty, score, dependency, max_step_cost, bottleneck_steps, hardest_strategy, strategy_uses, engine_version, seed, created_at |
| `campaign_levels` | level number → puzzle_id. Fixed forever once published. |
| `daily_puzzles` | date (UTC) → puzzle_id |
| `players` | id, firebase_uid (nullable at first), display_name, created_at |
| `attempts` | id, player_id, puzzle_id, started_at, completed_at, elapsed_ms, mistakes, hints_used |

Column notes:
- `share_code`: unique, random, about 6–8 characters from an unambiguous alphabet (no
  0/O, 1/l). Random rather than derived from `id`, so puzzles can't be found by stepping
  through sequential ids.
- `source`: `pool`, `campaign`, `daily` or `custom`. The random endpoint only serves
  `pool` puzzles, so it never hands out today's daily, a campaign level, or a player's
  custom puzzle. Assigning a puzzle to the campaign or a day moves it out of `pool`.
- `strategy_uses`: a JSONB map of strategy → times used, from
  `SolveTrace.getAllUseCounts()` (e.g. `{"NAKED_PAIR": 3, "X_WING": 1}`). Custom
  generation uses it to find a stored puzzle that matches a player's settings before
  generating a new one, so it's stored from the start to avoid a backfill later.
- `clue_count`, `max_step_cost`, `bottleneck_steps`: inputs for calibration (milestone 8),
  from `SolveTrace`. Stored now so calibration doesn't need to re-solve every puzzle.

Constraints and indexes:
- `difficulty` limited to the known values by a CHECK constraint (or a Postgres enum).
- `puzzles(source, difficulty)` for drawing from the pool.
- `attempts(puzzle_id, elapsed_ms)` for leaderboards.
- One leaderboard attempt per player per daily puzzle: a unique index on
  `(player_id, puzzle_id)` for daily puzzles, or a `counts_for_leaderboard` flag with a
  partial unique index. Later attempts are still recorded, just not ranked.

### Generator job
- Runs in a new `pidoku-jobs` module, or as a Spring profile of the API.
- Tops each difficulty up to N `pool` puzzles players haven't seen.
- Picks next month's daily puzzles ahead of time, moving them from `pool` to `daily`.
- Creates a fresh `PidokuBuilder` for each puzzle, because the engine isn't thread-safe.

### Security configuration
`spring-boot-starter-security` is already a dependency, and by default it requires
authentication on every endpoint. Milestone 1 needs a `SecurityFilterChain` that permits
the public read endpoints (and `/actuator/health`). Milestone 3 adds Firebase token checks.

### Endpoints
| Endpoint | Purpose |
|---|---|
| `GET /api/campaign` | Levels with lock, progress and star state for the player |
| `GET /api/campaign/{n}` | Givens for level n (only if unlocked) |
| `GET /api/puzzles/random?difficulty=hard` | Takes a `pool` puzzle, skipping ones the player has seen (once sign-in exists in milestone 3) |
| `GET /api/puzzles/daily` | Today's (UTC) puzzle |
| `GET /api/puzzles/{shareCode}` | A shared puzzle |
| `POST /api/puzzles/{id}/hint` | Current grid in → easiest next move from the human solver, or "this cell is wrong" |
| `POST /api/attempts` | Final grid and mistakes. The server checks the grid against the solution, records the attempt and unlocks the next level. |
| `GET /api/puzzles/daily/leaderboard` | Fastest times for the day (friends filter later) |

- Solutions stay on the server in principle. Checking mistakes live would then need a
  `POST /check` call or a per-cell hashed solution. First version: send the solution and
  accept that it can be cheated, since this is a game with friends.
- Validation: a stored puzzle id plus a completed grid that matches the solution.
- Tests: Testcontainers integration tests, plus one test per endpoint.

## 2. Deployment on Google Cloud

- **API on Cloud Run**
  - Image built with Jib and pushed to Artifact Registry.
  - min-instances=0, max-instances=3, 512Mi–1Gi memory, concurrency about 40.
  - Cold starts: try Spring Boot's Class Data Sharing support or Spring AOT first; consider
    GraalVM native only if it's still slow. Health checks via Actuator.
- **Generator job**: a Cloud Run Job triggered nightly by Cloud Scheduler. This ships in
  milestone 2, because the daily endpoint has nothing to serve until the job has picked
  daily puzzles.
- **Database on Neon**
  - Pooled connection string, Hikari pool of 5.
  - Neon pauses when idle, so the first query after that takes about a second.
  - Credentials in Secret Manager, passed to Cloud Run as environment variables.
- **Frontend on Firebase Hosting**: a rewrite sends `/api/**` to the Cloud Run service.
  Same origin means no CORS setup and one domain.
- **CI/CD with GitHub Actions**
  - Every PR: Gradle tests (with Testcontainers) and npm test, lint and build.
  - Merge to main: Jib build and `gcloud run deploy`, then `firebase deploy`.
  - Authenticate with Workload Identity Federation, so no service-account keys are stored.
- **Cost**: about $0/month at friends-scale traffic on the free tiers. Budget alert at about $5.
- **Observability**: structured JSON logs to Cloud Logging, plus an uptime check on
  `/actuator/health`.

## 3. Anonymous sign-in and players

Moved ahead of custom generation and campaign, because several earlier features need to
know who the player is: skipping puzzles already seen, unlocking campaign levels, rate
limiting custom generation, and `attempts.player_id`.

- Anonymous Firebase sign-in: every visitor gets a uid with no sign-up.
- API side: Spring Security checks the Firebase ID token, and a `players` row keyed by
  `firebase_uid` is created on first request.
- Starting a puzzle creates an `attempts` row with a server-side `started_at`, so solve
  times are measured by the server rather than claimed by the client.

## 4. Custom generation

Players build their own difficulty: which strategies may be used, min and max clue count,
and so on, then get a puzzle generated for it.

- The engine's `DifficultyProfile.Builder` already supports clue range, score range,
  strategy tier floor and ceiling, and a per-strategy use cap (a cap of 0 bans a strategy).
- Not yet supported: allowing individual strategies rather than whole tiers, and requiring
  a strategy ("must need an X-Wing").
- Endpoint sketch, `POST /api/puzzles/generate`:
  1. Validate the settings; `build()` already rejects impossible ones.
  2. Try a matching stored puzzle first (using `strategy_uses`), for an instant answer.
  3. Otherwise generate inside the request with a fresh builder and a short timeout. On
     timeout, suggest loosening the settings.
- Cloud Run throttles CPU once a response is sent, so generation stays inside the request
  rather than running in the background.
- Protecting the CPU: one generation can occupy a full CPU for several seconds, and at a
  concurrency of about 40 a few of them would slow every other request.
  - Rate limit per player.
  - Cap concurrent generations per instance (about 2) with a semaphore; extra requests
    get a "busy, try again" response.
  - If that isn't enough, run generation as a separate Cloud Run service with low
    concurrency so the rest of the API isn't affected.
  - `max-instances=3` already caps the cost, but not the slowdown.
- The shared-candidate solver speedup becomes worth doing here, since players wait on it.
- Generated puzzles are stored with `source = custom`, so they get share codes like any
  other puzzle but never enter the random pool.

## 5. Campaign

- A one-off script generates about 100 puzzles, sorts them by score and stores them as
  levels 1–100 (`source = campaign`).
- Difficulty rises gently, e.g. 10 Trivial → 15 Relaxing → 20 Easy → and so on.
- Once published, a level is never regenerated; new levels are added at the end.
- Frontend: a level map or grid with locks, and up to 3 stars per level (finished, no
  mistakes, under a target time). Progress synced to the server and cached in the browser.

## 6. Attempts, leaderboards and friends

- Daily: one per UTC day, a streak counter, and a share line like "Pidoku #123 4:12 ✓".
- Leaderboard uses the server-measured time from milestone 3, and only each player's
  first completed attempt at a daily puzzle counts.
- Cheating: the client still has the solution, so a determined player can cheat. That's
  accepted for a game among friends; server-side timing removes the easiest cheat
  (posting any time you like).
- Let players upgrade from anonymous to Google sign-in so progress follows them across
  devices.
- Friends: a friend code to start with, then the leaderboard is filtered to your friends.

## 7. Hints and share codes

- Hints button calls the hint endpoint, shows the strategy name ("Hidden single (box)")
  and highlights the cell. The server increments `attempts.hints_used`.
- Share codes: a URL like `/p/{shareCode}` opens that exact puzzle.

## Frontend overview

- An API client replaces the bundled puzzles. Offline JSON loading stays as a dev and
  fallback mode.
- Home screen: Campaign, Daily, Generate (custom difficulty) and Load JSON.

## 8. Calibrating difficulty

- `attempts` collects real solve times and mistakes.
- After a few hundred solves, fit a linear model predicting median solve time from:
  hardest step (`max_step_cost`), top-10 step sum, dependency, bottlenecks
  (`bottleneck_steps`) and `clue_count`. Train on half the puzzles and test on the other half.
- Replace the hand-picked weights, re-set the score ranges, and record the fit (r²) in the
  README.
- Re-score stored puzzles: a one-off job recomputes `score` and `difficulty` for every
  puzzle with the new weights. Campaign levels keep their order and their puzzles even if
  their labels change; pool puzzles may move between difficulties.

## Engine follow-ups

- Add an `engine_version` constant to the engine.
- Make sure each use of the builder gets its own instance.
- Tune the Trivial target, which is too easy at 65 clues.
- Shared-candidate speedup (needed for custom generation).

## Open items

- Thread safety: the generator and solver keep their own random number generators, so the
  API needs a fresh builder per request.
- Calibration: none of the scoring weights have been checked against real players.
- The new strategy names have only been checked on the puzzle cards, not in the game screen.
