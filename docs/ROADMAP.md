# Pidoku Roadmap

Pidoku is Sudoku using the first nine digits of π: every row, column and box holds
3 1 4 1 5 9 2 6 5, so 1 and 5 appear twice and 7, 8 and 0 never appear.
It has two goals: a game that's fun to play with friends, and a full-stack project that looks
good on a resume. The milestone order follows the first goal (what gets friends playing daily
comes first); the "Showcase" section covers the second.

## Decisions made

### Stack
- Java 25 engine and Spring Boot API in one Gradle build.
- React 19 + TypeScript + Vite frontend.
- Hosting: Cloud Run (scales to zero), Neon Postgres and Firebase Hosting.

### Puzzles are generated ahead of time
Campaign, daily and random puzzles are made by a background job and stored in the database,
not generated during a request (Expert and Fiendish take about 4–11 seconds). Stored puzzles
also keep share codes and campaign levels stable when the engine changes. Every puzzle is
stamped with an `engine_version`.

The one exception is **custom generation** (milestone 7), where the player designs their
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
- Every day has three dailies: one easy, one medium and one hard.
- "Today" is the **player's local date**. The frontend asks for the dailies of the date on
  the player's device, so a new day starts at each player's own midnight and everyone gets
  the same puzzles for the same calendar date (as Wordle does). A solve counts toward the
  leaderboard of the date and difficulty its puzzle belongs to, and streaks count consecutive
  local dates. (Changed from 00:00 UTC in milestone 2, which was mid-evening in the US.)
- Consequences accepted for a game among friends: a date's puzzles are "today's" for about 50
  hours worldwide, so a friend further east sees them first; a date's leaderboard stays open
  that long; and setting a device clock forward shows the next date a few hours early. The
  API limits that by only serving dates that are today somewhere in the world (yesterday to
  tomorrow in UTC).
- A complete day's response never changes, so Firebase Hosting's CDN caches it for a day
  (`s-maxage`); browsers don't (`max-age=0`), and an incomplete day isn't cached.
  `firebase deploy` clears the CDN cache, so an API change that alters the response needs a
  hosting deploy too.
- The number in "Pidoku #123" is days since launch (launch day is #1).

## Milestones

1. Schema, generator job and read endpoints, tested locally against Testcontainers. **Done.**
2. Deploy early: Cloud Run, the nightly generator job, Neon and Firebase with CI/CD. **Done.**
   CI/CD runs on GitHub Actions: every PR is tested, and a merge to main deploys (section 2).
3. Anonymous sign-in and players, so everything after this knows who the player is.
   **Done.**
4. The daily loop: a start screen, a result screen with the server's time, streaks, a share
   line, display names and share links. **Done.**
5. Leaderboards, Google sign-in and friends. **Done.**

   **— v1: the game is worth sending to friends and putting on a resume —**

   Done after v1, before milestone 6 (2026-11-21, see "After v1"): analytics events, "Explain a
   move", the tutorial, auto notes on Expert and Fiendish, Playwright end-to-end tests, progress
   sync between devices, and Recent in place of "Your puzzles".

   Order revised 2026-11-25:

   - First, decide how assisted solves rank (hints, mistakes, auto notes) in one go; see "Open
     items". Hints and the stats page both depend on it.
   - ~~Leaderboards for any shared puzzle (challenge links).~~ Done 2026-11-26, after hints in the
     end (see "After v1").

6. Hints (reusing the "explain a move" layer), with time penalties on the leaderboards for
   hints and mistakes. **Done** and deployed (2026-11-25; see section 6).

   Then ~~the player stats page~~ (done 2026-11-26), built after hints so assisted solves can be
   filtered from the start. Streak reminders are postponed until analytics show streaks breaking because people
   forget.

   Before milestone 7, engine work: the shared-candidate speedup (done 2026-11-26), then
   Pidoku-only techniques (the twin X-Wing), both in "Engine follow-ups", and the Trivial tune.

7. Custom generation: players build their own difficulty.
8. Campaign mode, end to end.
9. Calibrate difficulty once real data exists. (Collecting the data started in milestone 4.)
   **Done before milestone 8**, so campaign target times can use the calibrated model. Check the
   data monthly. It can jump ahead of anything above once there are a few hundred solves across
   a few dozen puzzles.

Why this order: the daily puzzles with a leaderboard, a streak and a share line are what bring
a group of friends back every day, and with attempts recorded (milestone 3) they're the
cheapest thing left to build. Custom generation and campaign are bigger and matter less to
that loop, so they come after v1; custom generation stays ahead of campaign. This replaces the
earlier order (custom generation, campaign, then leaderboards and friends).

## 1. Database, generator job and API

**Status: done.** Built in milestone 1: the `puzzles` and `daily_puzzles` tables, the generator
job, the random, daily and share-code endpoints, and the security configuration. See the README
for how to run them.

Decisions made while building it:
- Each table is created by the milestone that first uses it, not all up front: once a Flyway
  migration has run on Neon it can't be edited, so a table created before its feature exists
  would lock in guesses. `players` and `attempts` came with milestone 3, `campaign_levels` comes with
  milestone 8. The designs below are still the plan.
- The generator job is a Spring profile of the API (`generator`), not a separate module: one
  build and one image, run as the API service or as the Cloud Run Job.
- The API offers six difficulties: trivial, easy, medium, hard, expert and fiendish. They were
  renamed one step up (Relaxing → Easy, Easy → Medium, Medium → Hard, Hard → Expert) with the
  engine profiles unchanged; V7 relabels stored puzzles and dailies.
- Each day has three dailies, one easy, one medium and one hard (`pidoku.daily.difficulties`
  in `application.properties`). A daily is identified by its slot, (date, difficulty), and a
  foreign key on `(puzzle_id, difficulty)` makes the database refuse a puzzle of the wrong
  difficulty in a slot. If the job only managed some of today's slots, the endpoint serves
  the ones it filled.
- Share codes are 8 characters from a 31-character alphabet without 0/O or 1/I/L, and
  case-insensitive.
- Errors are RFC 9457 problem details. An empty pool or a missing daily is a 503 (the job will
  fix it), an unknown share code a 404.

### Schema (Flyway migrations in `pidoku-api`)
| Table | Columns | Created in |
|---|---|---|
| `puzzles` | id, share_code, source, size, givens and solution (81-character strings), clue_count, difficulty, score, dependency, max_step_cost, bottleneck_steps, hardest_strategy, strategy_uses, engine_version, seed, created_at | V1 (milestone 1) |
| `daily_puzzles` | (puzzle_date, difficulty) → puzzle_id | V2 (milestone 1) |
| `players` | id, firebase_uid, display_name (null until chosen), created_at; friend_code added in V6 | V3 (milestone 3), V6 (milestone 5) |
| `friendships` | (player_id, friend_id), both directions, created_at | V6 (milestone 5) |
| `attempts` | id, player_id, puzzle_id, started_at, completed_at, elapsed_ms, mistakes, hints_used; active_ms and late added in V4 | V3 (milestone 3), V4 (milestone 4) |
| `campaign_levels` | level number → puzzle_id. Fixed forever once published. | milestone 8 |

Column notes:
- `share_code`: unique, random, 8 characters from an unambiguous alphabet (no 0/O, 1/I/L).
  Random rather than derived from `id`, so puzzles can't be found by stepping through
  sequential ids.
- `source`: `pool`, `campaign`, `daily` or `custom`. The random endpoint only serves
  `pool` puzzles, so it never hands out today's daily, a campaign level, or a player's
  custom puzzle. A puzzle's source never changes: dailies (and campaign levels) are generated
  for that use, never taken from the pool, so no one can have played them through Random.
- `strategy_uses`: a JSONB map of strategy → times used, from
  `SolveTrace.getAllUseCounts()` (e.g. `{"NAKED_PAIR": 3, "X_WING": 1}`). Custom
  generation uses it to find a stored puzzle that matches a player's settings before
  generating a new one, so it's stored from the start to avoid a backfill later.
- `clue_count`, `max_step_cost`, `bottleneck_steps`: inputs for calibration (milestone 9),
  from `SolveTrace`. Stored now so calibration doesn't need to re-solve every puzzle.

Constraints and indexes:
- `source` and `difficulty` limited to the known values by CHECK constraints, and grids to
  the right length and digits.
- `puzzles(source, difficulty)` for drawing from the pool.
- `attempts(puzzle_id, elapsed_ms)` over finished attempts, for leaderboards (created in V3,
  used from milestone 5).
- A player has at most one unfinished attempt per puzzle (a partial unique index); finished
  ones aren't limited, so puzzles can be replayed.
- One leaderboard attempt per player per daily puzzle (milestone 5): the player's first
  finished attempt counts. Later attempts are still recorded, just not ranked.

### Generator job
- Runs as the `generator` Spring profile of the API, with no web server.
- Fills the daily slots (easy, medium and hard) for the next 31 days with newly generated
  puzzles, first, since they're what players wait for. Once the 31 days are filled, a normal
  night generates three.
- Then tops up the pool, generating in parallel: each difficulty gets enough new puzzles that
  the player who has started the most of its pool puzzles still has N (default 50) they haven't
  started. So the pool grows as people play; with no one playing it holds N. A run never adds
  more than N of a difficulty, since no one can have started more than the pool holds.
- Pool puzzles are never removed: attempts and share links point at them, they're tiny, and
  the random endpoint skips the ones a player has started.
- Until milestone 4, dailies were taken from the pool, so a daily could be a puzzle someone had
  already played through Random. Migration V5 cleared the slots no one had been served yet
  (two or more days ahead) and put their puzzles back in the pool.
- Shares one `PidokuBuilder` across threads: the engine is thread-safe, with each call's
  random state created from its seed inside `generate`.
- Random puzzles: signed in, the endpoint skips puzzles the player has started (finished or
  not). If they've started them all before the next night's top-up, it sends the one they started
  longest ago with `"repeat": true`, and the frontend says "You've played every hard puzzle.
  Here's one again." and starts it fresh (old progress wiped, the server's clock restarted).
  The frontend also asks again, up to five times, when it gets a puzzle this browser has
  progress on that the server doesn't know about (played signed out, or the start never arrived).

### Security configuration
`spring-boot-starter-security` is already a dependency, and by default it requires
authentication on every endpoint. Milestone 1 needs a `SecurityFilterChain` that permits
the public read endpoints (and `/actuator/health`). Milestone 3 added Firebase token checks
(section 3).

### Endpoints
| Endpoint | Purpose | Milestone |
|---|---|---|
| `GET /api/puzzles/random?difficulty=hard` | Takes a `pool` puzzle. Sign-in is optional; signed in, it skips puzzles the player has started, and once they've started them all sends a `repeat` | 1, 3, 4 (done) |
| `GET /api/puzzles/daily/{date}` | A date's easy, medium and hard puzzles; the frontend sends the player's local date | 1 (done) |
| `GET /api/puzzles/{shareCode}` | A shared puzzle, with `dailyDate` if it's a daily | 1, 4 (done) |
| `GET /api/players/me` | The signed-in player, created on first use; with their friend code (milestone 5) | 3, 5 (done) |
| `POST /api/attempts` | Puzzle id in → the player's unfinished attempt at it, started now if there isn't one. Starts the server's clock. | 3 (done) |
| `POST /api/attempts/{id}/complete` | Final grid and mistakes. The server checks the grid against the solution and records the time since the start. Takes `activeMs` and `lateByMs` (milestone 4), and unlocks the next campaign level in milestone 8. | 3, 4 (done), 8 |
| `PATCH /api/players/me` | Sets the display name | 4 (done) |
| `DELETE /api/players/me` | Deletes the player, their attempts and friendships (the app then deletes the Firebase account) | after v1 (done) |
| `GET /api/players/me/streak?today=2026-11-17` | Current and best streak, as of the player's local date | 4 (done) |
| `GET /api/puzzles/daily/{date}/leaderboard?difficulty=hard&friends=true&clean=true` | Fastest times for one of the day's dailies; `friends` for friends only, `clean` for solves with no mistakes only. Sign-in optional (needed for `friends`) | 5 (done) |
| `GET /api/friends?today=…`, `POST /api/friends`, `DELETE /api/friends/{id}` | List (with each friend's current streak when `today` is given), add by friend code, remove | 5 (done) |
| `GET /api/friends/{id}?today=…` | One friend's profile: streaks, dailies solved, clean rate, best time per difficulty, last 7 days of dailies. 404 unless you're friends | 5 (done) |
| `GET /api/puzzles/daily/{date}/summary` | For the home screen's cards: how many have solved each of the date's dailies and the fastest time; signed in, also how many friends have and the fastest among them. Counted like the leaderboards | after v1 (done) |
| `PUT /api/attempts/{id}/progress` | The board, notes, mistakes and timer of an unfinished attempt, so it continues on another device. Handed back by `POST /api/attempts`; cleared on restart and on the solve | after v1 (done) |
| `GET /api/players/me/recent` | The player's last 10 puzzles, unfinished first, for Recent | after v1 (done) |
| `GET /api/puzzles/{shareCode}/leaderboard?friends=true&clean=true` | Any puzzle's fastest times by share code (challenge links), ranked like a daily's. A daily keeps its date rule; any other puzzle ranks every first solve | after v1 (done) |
| `GET /api/players/me/stats?today=…&source=all\|daily\|random&assisted=true` | The player's own stats per difficulty (solved, average, median, best, clean, mistakes, last 30 days), each puzzle's first solve, ranked times. Assisted solves (hints or auto notes) left out unless `assisted=true` | after v1 (done) |
| `POST /api/attempts/{id}/hint` | The board in, the next hint step out (where, what to look for, the answer), charged to the attempt | 6 (done) |
| `POST /api/events` | Up to 50 analytics events (share, share_arrival, abandoned, tutorial_completed, tutorial_skipped) | after v1 (done) |
| `GET /api/players/me/stats?source=all\|daily\|random&today=…` | The player's stats page: solves, average and best times per difficulty, clean rate, streaks | after 6 |
| `GET /api/puzzles/{shareCode}/leaderboard` | Fastest times for any puzzle, not just dailies (challenge links) | after 6 |
| `POST /api/puzzles/{id}/hint` | Current grid in → easiest next move from the human solver, or "this cell is wrong" | 6 |
| `POST /api/puzzles/generate` | A puzzle for the player's own difficulty settings | 7 |
| `GET /api/campaign` | Levels with lock, progress and star state for the player | 8 |
| `GET /api/campaign/{n}` | Givens for level n (only if unlocked) | 8 |

- Solutions stay on the server in principle. Checking mistakes live would then need a
  `POST /check` call or a per-cell hashed solution. First version: send the solution and
  accept that it can be cheated, since this is a game with friends.
- Validation: a stored puzzle id plus a completed grid that matches the solution.
- Tests: Testcontainers integration tests, plus one test per endpoint.

## 2. Deployment on Google Cloud

- **Image** (done, `scripts/build-image.sh`)
  - Built with Jib (`packaged` mode) on `eclipse-temurin:25-jre-noble`, pinned by digest, running
    as an unprivileged user. One image for the API and the generator job.
  - JDK 25 AOT cache, built in two passes: Jib builds the image, a training run against a
    throwaway Postgres records the cache, Jib rebuilds with it, and a run with `-XX:AOTMode=on`
    checks it's usable. Ready Spring context on one CPU: about 8 s → 4 s.
  - Fixed JVM flags: Serial GC (what the JVM picks on one vCPU anyway, pinned so training and
    production match), compact object headers, `MaxRAMPercentage=75`.
  - Jib uses `productionRuntimeClasspath`, so devtools stays out of the image.
  - Spring AOT (`processAot`) for the service only, built with the `api` profile. Its code only
    runs with `-Dspring.aot.enabled=true`, which the service sets in `JAVA_TOOL_OPTIONS`, so the
    same image still runs the `generator` and `migrate` profiles without it.
  - No database work on the service's cold start: migrations run in CI (the job with the
    `migrate` profile) before the deploy, Hibernate neither validates nor reads metadata, and the
    first connection opens on a background thread.
  - The AOT cache is trained on a round of real requests, not just startup.
  - Measured on a private copy of the service (4 cold starts each, Spring's "process running
    for"): 8.7 s before, 7.1 s with these changes on gen1, 4.7 s on gen2. Locally, on one CPU:
    about 3.5 s instead of 5 s.
  - CI's deploy sets gen2, the AOT flag and the probes on every deploy (see ci.yml). The startup
    probe is an HTTP check of `/actuator/health/readiness` every second, with no readiness probe:
    with the old TCP startup probe plus a readiness probe every 10 s, the readiness probe failed
    on most cold starts (Spring reports ready a moment after the port opens), and the new instance
    then took no requests for 10–20 s.
  - GraalVM native only if cold starts are still too slow once measured on Cloud Run.
- **API on Cloud Run**
  - Image pushed to Artifact Registry.
  - min-instances=0, max-instances=3, concurrency about 40. Memory: the API used about 225 MiB
    after startup in local testing, so 512Mi may be enough; start at 1Gi and check.
  - `--cpu-boost` for startup. Health checks via Actuator.
- **Generator job**: a Cloud Run Job triggered nightly by Cloud Scheduler. This ships in
  milestone 2, because the daily endpoint has nothing to serve until the job has picked
  daily puzzles.
- **Database on Neon**
  - Pooled connection string, Hikari pool of 5.
  - Neon pauses when idle, so the first query after that takes about a second.
  - Credentials in Secret Manager, passed to Cloud Run as environment variables.
- **Frontend on Firebase Hosting**: a rewrite sends `/api/**` to the Cloud Run service.
  Same origin means no CORS setup and one domain.
- **CI/CD with GitHub Actions** — **working.** The earlier billing issue is resolved, and
  `.github/workflows/ci.yml` runs on every pull request and every push to main (runs passing
  as of PRs #4–#6). Deploying by hand (README, "Deploying by hand") remains the fallback.
  - Every PR: Gradle tests (with Testcontainers) and npm test, lint and build.
  - Merge to main: Jib build and `gcloud run deploy`, then `firebase deploy`.
  - Authenticate with Workload Identity Federation, so no service-account keys are stored.
- **Cost**: about $0/month at friends-scale traffic on the free tiers. Budget alert at about $5.
- **Observability**: structured JSON logs to Cloud Logging, plus an uptime check on
  `/actuator/health`.

## 3. Anonymous sign-in and players

Comes before everything else that needs to know who the player is: attempts, streaks,
leaderboards, skipping puzzles already seen, rate limiting custom generation and unlocking
campaign levels.

**Status: done.** The Firebase project has the Anonymous provider enabled and a web app
(`pidoku-web`), whose config is in `pidoku-frontend/src/api/firebaseConfig.ts`. Checked with a
real anonymous account: Firebase issues a token and the API accepts it (and refuses a tampered
one). Deployed on 2026-11-17 with milestone 4.

- Anonymous Firebase sign-in: every visitor gets a uid with no sign-up.
- API side: Spring Security checks the Firebase ID token, and a `players` row keyed by
  `firebase_uid` is created on first request.
- Starting a puzzle creates an `attempts` row with a server-side `started_at`, so solve
  times are measured by the server rather than claimed by the client.

Decisions made while building it:
- The API verifies tokens as a plain OAuth2 resource server (Google's public keys, issuer and
  audience), not with the Firebase Admin SDK: no service-account credentials, and nothing extra
  to load at cold start. The Admin SDK would only be needed to revoke tokens or manage users.
- The frontend loads the Firebase SDK on first use, in its own chunk, and signs in the first
  time it needs a token (a random puzzle or an attempt), not on page load.
- The daily request never carries the token, so the CDN keeps caching it.
- `firebase_uid` is NOT NULL: with sign-in arriving before players do anything, there's no
  player without one.
- Recording a solve is two calls, start and complete, rather than the one `POST /api/attempts`
  first sketched: the server can only measure the time if it saw the start.
- A solve time is wall-clock time from starting the puzzle to solving it, including time
  away. The timer on screen pauses while the tab is hidden, so it can read less than the
  recorded time (milestone 4 deals with this).
- Opening a puzzle with an unfinished attempt continues that attempt, on any device; solving
  it and playing again starts a new one.
- The Restart button starts the attempt's clock again (`"restart": true`), so the server's time
  matches the timer on screen. That lets a player look at a puzzle, restart and solve it fast;
  milestone 5 decided: Restart on a daily clears the board but keeps its clock.
- A solve the API couldn't be told about (offline, API down) is kept in localStorage and sent
  when the page next loads or the connection returns. Its recorded time runs until it arrives,
  so it's slower than the real one, never faster. Milestone 4 corrects this ("Late-sent
  solves").
- `mistakes` is whatever the client reports. Like the solution being sent to the client, it
  can be cheated.
- Sign-in is best-effort in the frontend: offline or blocked, the game plays as before and the
  attempt just isn't recorded.

## 4. The daily loop

What a player sees around a daily puzzle, before any leaderboard: enough to play every day and
to tell friends about it.

**Status: done.** Deployed on 2026-11-17 (migrations V4 and V5 ran on Neon), and checked on the
live site: the new endpoints and the manifest are served, and a daily played on a real phone
looks right. Before that, tested with the API's integration tests, the frontend's unit tests,
and by playing a daily end to end in a phone-sized headless Chromium against a local API. The
nightly generator job is scheduled with Cloud Scheduler (`pidoku-generator-nightly`, 03:00
America/New_York).

Decisions made while building it:
- **Launch date: 2026-11-17** (`LAUNCH_DATE` in `pidoku-frontend/src/game/daily.ts`), so that
  date is Pidoku #1. Change it before anyone shares a result, never after.
- Share line: `Pidoku #123 Hard 4:12 ✓` with the `/p/{shareCode}` link on the next line. ✓ means
  no mistakes; otherwise it says how many, e.g. `2✗`. The time is the server's when it has one.
  Other API puzzles get the same line without a number; puzzles from a file get no link.
- A daily is any puzzle with a `dailyDate`, which `GET /api/puzzles/{shareCode}` now returns too,
  so a daily opened from a friend's share link also gets the start screen and the share line.
  Changing the daily response needs a hosting deploy to clear the CDN (see "Daily puzzle rules");
  the frontend also takes the date from the response, so an old cached copy still works.
- The start screen hides the board, so it can't be studied before the clock starts. Nothing is
  saved before Start, so a daily with saved progress opens straight to the board.
- Streaks: `GET /api/players/me/streak?today=…`, computed from attempts on every request rather
  than stored. A solve counts for its date only if it happened while that date was today
  somewhere (from its midnight at UTC+14 to its midnight at UTC-12). That stops a missed day being
  filled in later through an old daily's share link, and stops an attempt from before the puzzle
  was a daily (the pool problem in Open items) from counting.
- The home screen shows the streak only for players who've seen a result before (it's cached in
  localStorage), so first-time visitors still aren't signed in on page load.
- Late solves: `completed_at` is the solve time the client claims (arrival minus `lateByMs`), never
  before `started_at`, and `elapsed_ms` runs from start to that. `late` is true when `lateByMs` is
  over 0. Solves queued before this change have no solve time, so they're sent without
  `lateByMs` and timed as before.
- Display names: trimmed, inner whitespace collapsed to one space, 1–40 characters counted as
  Postgres counts them (code points, so an emoji is one), no control or invisible characters
  (zero-width joiners are allowed, for emoji). Asked for under the first recorded result; "Not
  now" is remembered in this browser and the prompt doesn't come back. There's no other place to
  change the name yet; milestone 5's leaderboards need one.
- Router: React Router (declarative mode). Routes are `/`, `/p/{shareCode}` and
  `/local/{id}` for puzzles that only exist in this browser. Opening a puzzle from the home
  screen passes it in the history state, so only a share link opened cold fetches it.
- Phones: the number pad was already one row of seven under the board. Added `touch-action:
  manipulation` (fast taps don't double-tap zoom; pinch zoom still works), 16 px inputs (iOS
  zooms into smaller ones), safe-area padding, a web app manifest, a π icon (also the new
  favicon, replacing Vite's) and an Apple touch icon. No service worker: browsers install
  without one now, and caching the app shell would fight `firebase deploy`'s cache clearing.

- **Start screen for dailies.** Opening a daily shows its difficulty and a Start button, and the
  attempt (the server's clock) starts on Start, not on opening. The ranked time is wall-clock,
  so the start has to be deliberate, and the screen says the clock keeps running once started.
  Other puzzles keep starting when opened.
- **Result screen.** On solving a daily: the server-measured time, mistakes, the streak, and
  the share line.
- **Share line**, copied to the clipboard: "Pidoku #123 Hard 4:12 ✓" plus a link. The number is
  days since launch (launch day is #1), so the launch date gets fixed here.
- **Streaks.** A day counts if the player solved any of that date's dailies, so an easy solve
  keeps a streak alive. Streaks count consecutive local dates, computed on the server from
  finished attempts at daily puzzles. Show current and best.
- **Display names.** `PATCH /api/players/me` sets `display_name` (trimmed, 1–40 characters); the
  frontend asks for it the first time a result is shown. Needed before leaderboards.
- **Share links.** `/p/{shareCode}` opens that exact puzzle; the endpoint has existed since
  milestone 1. The frontend has no URLs yet (the open puzzle is React state), so this adds a
  router, which campaign levels will need too. firebase.json already serves index.html for
  every path.
- **Active time.** Add `attempts.active_ms`, the client's own timer (paused while the tab is
  hidden), sent with the completing call. Leaderboards first ranked the server's `elapsed_ms`
  and have ranked `active_ms` since 2026-11-19 (section 5); `active_ms` is also for calibration,
  where time away is noise. It goes in now because it can't be
  backfilled: every solve recorded without it is lost to calibration.
- **Late-sent solves.** A solve finished offline or while the API was down is sent later
  (milestone 3), and today its recorded time runs until it arrives. Fix it in the same migration
  and request change as `active_ms`:
  - The frontend saves when the puzzle was solved alongside the queued completion, and sends
    how long ago that was (`lateByMs`) when it finally gets through.
  - The server records `elapsed_ms` minus that delay, never below zero, and sets
    `attempts.late` (false for a completion sent straight away).
  - Leaderboards (milestone 5) show late solves with a marker rather than leaving them off, so
    a dropped connection doesn't cost anyone their result, and they count for streaks.
  - The delay is the client's claim, so it can be faked for a faster time. Accepted like the
    other cheats, since the solution is in the browser anyway; the marker makes it visible to
    friends. If the game ever goes beyond friends, late solves should keep counting for streaks
    but stop ranking.
- **Phones.** Friends will mostly play on a phone, so check the game screen there (number pad
  reach, board size, no accidental zoom) and add a web app manifest and icons so it installs to
  the home screen.

## 5. Leaderboards, Google sign-in and friends

**Status: done.** Tested with the API's integration tests, the frontend's unit tests, and end to
end in a phone-sized headless Chromium against a local API and local Postgres, with real anonymous
Firebase sign-in: naming the player, adding friends by code and by invite link, solving a daily and
seeing it on both leaderboards. The Google sign-in provider is enabled in Firebase and the
milestone is deployed (migration V6 ran on Neon). If a custom domain is added, add it to
Firebase's authorized domains too (`pidoku.web.app` and `localhost` are authorized by default).

Decisions made while building it:
- **Dailies don't restart the clock.** Restart on a daily clears the board but keeps the time and
  the mistakes, on screen and on the server (`AttemptService.start` ignores `restart` for dailies).
  Otherwise a player could study the board, restart and post a fast time.
- **Ranking:** each player's first finished attempt, by `active_ms` (falling back to `elapsed_ms`
  for solves recorded without one); equal times share a rank.
  Top 50, plus the viewer's own entry wherever it falls. A player who started the puzzle before its
  date began anywhere (a pool puzzle before V5) isn't ranked. Unlike streaks, solving an old daily
  later through a share link does rank: the board is about the puzzle, not the day. Computed per
  request, not cached (it changes with every solve).
- **Two rankings:** Fastest (time only; mistakes are shown but don't count) and Clean (only solves
  with no mistakes). Both use each player's first solve, so a clean replay after a messy first solve
  doesn't make the clean board. The switch is remembered like Everyone/Friends.
- **Fun facts, cheap on the database.** Worked out in the browser from the leaderboard response
  (`game/leaderboardStats.ts`): "Faster than N% of solvers", your place among friends, the gap to
  the players either side, and "photo finish" for times under a second apart. An extra column in
  the same ranking query: the fastest clean solve, returned even when beyond the top 50. (A "first
  to finish" highlight was here too, removed in Nov 2026.) Friends' current
  streaks come from one query for all friends when `/friends` opens, as of the viewer's date (it can be a
  day out for a friend far away). Head-to-head records are left for later.
- **Friend profiles** (`/friends/{id}`, opened from the friend list): streak and best streak,
  dailies solved, clean rate, best time per difficulty and their last week of dailies, all from
  their first solve of each daily (as ranked). Two queries on that one friend's rows. Only friends
  can see them; anyone else gets a 404, so ids can't be probed for stats. Dates before launch day
  show without a "Pidoku #" number.
- **Where:** under the result of a daily (once the solve is saved, so it includes the player), and
  at `/leaderboard/{date}?difficulty=hard`, linked from Today on the home screen. Tabs for
  Everyone and Friends; the last one picked is remembered. Players without a name show as
  Anonymous; late solves get a cloud-off icon.
- **Friends are mutual and instant.** Adding someone's code makes both players friends, no request
  to accept: codes are only shared with people you know. Stored as two rows per friendship. Each
  player gets a friend code (share-code alphabet) the first time they open `GET /api/players/me`;
  it never changes. Invite links are `/f/{code}`.
- **Friends and You** (split from one "You & friends" page on 2026-11-26, which mixed friends,
  sign-in and looks in one long scroll). The home screen links both.
  - `/friends`: the friend code with Copy link / Share, adding by code, and the friend list, with
    a nudge to pick a name while the player has none.
  - `/me` ("You"): the name (the place to change it that leaderboards needed), a "Keep your
    progress" card with Google sign-in for guests, and a menu: Your stats (`/me/stats`), Your
    profile (`/players/{id}`), Look & feel (`/me/look`), Support Pidoku (`/me/support`) and
    Account & privacy (`/me/account`: Google sign-in and sign-out, who can see the profile, a
    link to `/privacy`, and deleting the account).
- **Google sign-in** is a popup (`linkWithPopup`), which keeps the uid. If that Google account is
  already a player, this browser switches to it (`signInWithCredential`): unsent solves are sent
  first, then the anonymous player is merged into the Google one (`POST /api/players/me/merge`
  with the anonymous ID token: attempts, events, friends and supporter badge move over, and the
  anonymous player is deleted, so it leaves the leaderboards), and the cached streak and
  dismissed prompts are cleared. Signing out gives the browser a
  fresh anonymous player. Asked for under a daily's result once the streak is 3 days ("Not now"
  is remembered), and always available on `/me` and `/me/account`.

- One leaderboard per daily (date and difficulty), ranked by `active_ms`, the game's own timer
  (changed from the server's wall-clock `elapsed_ms` on 2026-11-19: time spent away from the tab
  shouldn't count). Solves without `active_ms` rank by `elapsed_ms`. This makes the ranked time the
  client's claim; cheating is set aside for now.
  Solves sent late (`attempts.late`, milestone 4) are ranked too, with a marker.
  Only each player's first finished attempt at a daily counts; later ones are recorded but not
  ranked. Shown on the result screen and from the day's puzzle list.
  - The game's clock is measured with `performance.now()`, not by counting interval ticks
    (browsers delay and throttle intervals, so the old clock ran slow on phones). It runs only
    while the board is on screen: switching tabs or going back to Puzzles pauses it, the time so
    far is saved, and it carries on when the player returns. The start screen and a line under
    the controls say so.
  - The server caps `active_ms` at `elapsed_ms`: time on screen can't be more than the time
    since the start, so a client can't claim an impossible time (2026-11-20).
  - Leaving and coming back means a player can look at the board, leave (paused) and work it
    out elsewhere. Accepted, like the other cheats.
- Cheating: the client still has the solution, so a determined player can cheat. That's
  accepted for a game among friends; server-side timing removes the easiest cheat
  (posting any time you like).
- **Google sign-in.** Let players upgrade the anonymous account to Google (Firebase account
  linking keeps the same uid, so the `players` row and its attempts carry over). Until they do,
  the account lives in one browser's storage: clearing it loses the streak. Prompt for it once
  there's a streak worth keeping. To handle: signing in on a second device with a Google
  account that's already linked means switching to that account and dropping the device's
  anonymous one.
- **Friends.** A friend code to start with, then the leaderboard can be filtered to friends.
  Needs a `friendships` table.

**v1 ends here.** Before calling it v1, do the Showcase items below marked v1.

## After v1

Smaller pieces after v1. The ones before hints (milestone 6) are listed in the order to build
them; challenge links and streak reminders come after hints and before custom generation.

### Done after v1 (2026-11-20)
- **Daily cards show who has solved them**, not the clue count and engine score, which meant
  nothing to players: the hardest technique the engine needed ("Hardest step: Hidden pair"), how
  many have solved it and the fastest time. With friends who've solved it, the card is about them
  ("3 friends solved", fastest among the player and their friends); otherwise it's everyone's
  numbers. One request for all three cards, `GET /api/puzzles/daily/{date}/summary`, counted
  like the leaderboards. It only sends a token for a browser that has signed in before
  (`existingIdToken`), so a first-time visitor still isn't signed in, or sent the Firebase SDK,
  on the home screen.
- **"Load a puzzle" is gone from the home screen.** It was a developer tool taking up half a
  phone screen. Engine files still work as bundled puzzles (`pidoku-frontend/src/puzzles`).
- **Next daily.** After solving a daily, the main button goes to the next of that date's dailies
  not yet solved ("Next: Daily Medium"); "More puzzles" once they're all solved.
- **Share button** on the result screen, opening the phone's share sheet (where there is one),
  next to Copy.
- **Link previews**: Open Graph tags and a 1200×630 card (`public/og-image.png`), so share and
  invite links show the game in iMessage, WhatsApp and Discord. No `og:url`, which would send
  every preview to the home page. The merged charset / site-verification meta tag was split.
- **Bigger, darker notes**, biggest on phones.
- **Wrong digits only are red.** A given (or a right digit) clashing with a wrong one gets a thin
  outline instead, so one mistake no longer paints a whole box red.
- **Leaderboards for any date**: previous/next day buttons and the browser's date picker, from
  launch day to the player's today.
- Wording: the leaderboard and result screen no longer say the server times solves; the ranked
  time is the game's clock.

### Done after v1 (2026-11-21)
Built in the order planned, so each change after analytics can be measured against what came
before.

**Analytics events.** An `events` table (V8: player, type, puzzle, a JSONB detail of at most
1000 characters, created_at) and `POST /api/events`, which takes batches of up to 50.
- Types: `share` (Share or Copy on a result, with the method), `share_arrival` (a visit from a
  share link, which carries `?ref=share`, with the share code and whether this browser had never
  signed in), `abandoned` (leaving an unfinished API puzzle after at least 5 s, or closing its
  tab: cells filled and left, active time, mistakes), `tutorial_completed` and `tutorial_skipped`.
  Auto notes are on `attempts` (`auto_notes_used`), not an event. Hints will be recorded the same
  way in milestone 6.
- The browser queues events in localStorage (at most 50) and sends them only with an account it
  already has, so recording one never signs a visitor in. A first visit's `share_arrival` waits
  until the player starts a puzzle, then goes with `occurredAt` saying when it happened (clamped
  to the last 7 days). Events the API refuses for good (a 4xx) are dropped so they can't block
  the queue. A closing page sends with `keepalive` and the cached token.
- `?ref=share` is dropped from the address once counted, so a reload or a copied link doesn't
  count again. Deleting an account deletes its events. The privacy page says what's collected.

**Explain a move** (engine, `explain/`). Every strategy now attaches a `Move.Reason` (the units,
the pattern cells and the digits), and `MoveExplainer` turns a move into an `Explanation`: the
cell and digit, the eliminations, the units to shade, the pattern, the "blockers" (filled cells
that rule the digit out elsewhere) and one sentence. The doubled digits have their own wording
("Row 4 already has one 5 and needs its second, and this is the only cell left in it that can
take a 5"; "…still needs both its 5s, and only these two cells can take a 5"). The sentence never
names the technique: `strategies.ts` stays the one source of player-facing names.
`HumanPidokuSolver.nextMove` exposes the solver's next step (same choice as before, so scores
didn't change); hints will use it. A property test explains every move every strategy finds on
300 boards and checks the highlighted cells against the board.

**Tutorial** (`/tutorial`, "How to play" on the home screen). Shown before the first puzzle a
browser opens (a browser that had played or signed in before counts as having seen it), before
any start screen or clock. Eight lessons on a nearly full 9×9 board: the rule, a box single, a
row needing its second 5, pencilling a 1 into two cells, placing the row's second 1 and watching
the other note clear itself, mistakes and undo, then the last cells with a "Show me" that
highlights the next step. A wrong digit gets its own message. Not timed and not an attempt.
- The board and the wording come from the engine: `./gradlew :pidoku-engine:exportTutorial`
  writes `pidoku-frontend/src/tutorial/tutorial.json` (the guided steps are checked to be the
  singles they claim to be), and `TutorialExportTest` fails while the file is out of date. The
  lessons around the steps (the rules and controls) are written in `tutorial/lessons.ts`.
- A 9×9 board rather than a Trivial one, so the doubled 1s and 5s look as they do in play.
- The board takes a highlight (shaded units, the pattern, a pulsing target, ringed blockers),
  which hints will reuse.

**Auto notes** on Expert and Fiendish: a button that pencils the candidates into every empty
cell, by the Pidoku rule (a digit stays a candidate until the unit holds as many copies as π
allows). Decided: a cell with notes keeps only those still possible (the player's narrowing is
respected); a cell without notes gets all its candidates. Wrong digits on the board don't count
against candidates. One step on the undo stack. Its use is saved with the progress and the
completion (`attempts.auto_notes_used`, V9, once true stays true), so calibration can tell
assisted solves apart.

**Progress sync between devices.** `PUT /api/attempts/{id}/progress` saves an unfinished
attempt's board, notes, mistakes and game timer (`attempts.progress` JSONB and
`progress_updated_at`, V9; the API checks the board fits the puzzle). The game sends it at most
every 10 s while the board changes, and with `keepalive` when the page is hidden or closed or the
player leaves. `POST /api/attempts` hands it back; the game takes it if it's newer than this
browser's copy (local saves now carry `savedAt`) and says "Carried on from where you left off on
another device." Last write wins. Restarting or solving clears it.

**Recent** in place of "Your puzzles": the last 10 puzzles, unfinished first ("Continue", with
the timer so far), then solves with their times. From `GET /api/players/me/recent` for a browser
that has signed in (dailies included, one row per puzzle preferring the unfinished attempt), plus
any of this browser's puzzles the server doesn't know; this browser's list otherwise. Puzzles
falling off this browser's list lose their saved progress, except dailies (that's what marks
today's cards solved). The list keeps its old localStorage key, so nothing played before is lost.
Custom puzzles can no longer be removed by hand; they age out.

**Playwright end-to-end tests** (`pidoku-frontend/e2e`, see its README). The real API on a
Testcontainers Postgres (`./gradlew :pidoku-api:bootTestRun`: the test classpath's
`E2eApiApplication`, the `e2e` profile, run from `pidoku-api/build/e2e` so `.env` is never
loaded) and the `VITE_E2E_AUTH=1` build (`dist-e2e/`, never deployed) under `vite preview`.
- Sign-in: the e2e build never loads Firebase; each browser sends `Bearer e2e.<uid>`, which only
  the e2e profile's `JwtDecoder` accepts (a dot, since bearer tokens can't hold a colon). Tests
  pick the uid, so two contexts can be one player on two devices.
- Today's dailies are generated by the real engine before the server takes requests.
- Covered, at desktop and phone sizes in UTC: solving a daily (result, streak, share line,
  leaderboard, Next, Recent), the clock pausing while away, a friend added by code on the home
  card and both leaderboard tabs, the date picker, progress carrying over to a second device,
  the tutorial (skipped, and played through with a mistake), and the share, share-arrival and
  abandoned events (and no abandoned event on a solve).
- CI runs them on every pull request (the `e2e` job), and deploy waits for them.

### Leaderboards for any shared puzzle (challenge links)
**Done 2026-11-26.** As built: `LeaderboardService.forPuzzle` shares the daily board's ranking
query; the result screen of every API puzzle shows its board; a share link to a non-daily that
someone else has solved opens on a start screen with the time to beat (the fastest friend if the
browser is signed in and a friend has solved it, else the fastest overall) and the clock waits for
Start, like a daily. With no one else's solve it opens straight to the board, as before. The e2e API
now seeds a few easy pool puzzles for tests like this.

The plan, as written before building it:

Only dailies have leaderboards, and dailies stop at Hard, so Expert and Fiendish are played
alone.
- `GET /api/puzzles/{shareCode}/leaderboard`: the same ranking (each player's first solve, by the
  game's timer) for any puzzle. Shown on the result screen of every API puzzle, not just dailies.
- Opening a share link to a non-daily shows the time to beat ("Alex solved this in 12:31").
- Turns any random Fiendish into a challenge between friends. Cheaper than custom generation
  (the ranking query exists) and more social, so it comes first.
- No "date began" rule as for dailies: a pool puzzle has no date, and a player who saw it before
  sharing it is fine among friends.

### Player stats page
**Done 2026-11-26.** As built: `stats/StatsService` reads the player's first finished attempt per
puzzle (a daily only if started once its date began, as on the leaderboards) and sums it up in
Java: per difficulty, solves counted, assisted solves (always reported, so the page can say how
many it left out), average, median, best (ranked times, penalties included), clean solves, average
mistakes, and the last 30 days' count and average. "Random" means pool puzzles that never became a
daily. The page (`/me/stats`, linked from the You page and from the result screen) has a source
switch and an "Include assisted solves" switch, off by default, both remembered in the browser. The
trend line compares the last 30 days with all time and stays quiet when every solve is recent.

The plan, as written before building it:

The friend profile (milestone 5) shows a few numbers about a friend; players have nothing as
detailed about themselves.
- A **Stats** page (`/me/stats`, linked from You & friends and the result screen).
- **Per difficulty** (trivial to fiendish): puzzles solved, average time, median time, best time,
  clean rate (solves with no mistakes) and average mistakes.
- **Filter by source**: All, Dailies only, or Random only. The filter is remembered like the
  leaderboard's switches.
- Also: current and best streak, dailies solved, and a simple trend (average time per difficulty
  over the last 30 days against all time), so players can see themselves improving.
- Times are the ranked time (the game's timer). Count each puzzle's first finished attempt, like
  the leaderboards, so replays of a solved puzzle don't pull the averages down; unfinished and
  late-only edge cases follow the leaderboard rules.
- One endpoint, `GET /api/players/me/stats?source=all|daily|random`, computed from `attempts`
  joined with `puzzles` (`source`, `difficulty`) per request; cheap at friend scale. The friend
  profile could later reuse it for a friend's page.
- Once hints and auto notes exist, show assisted solves separately (or a filter for them), so
  averages compare like with like.

### Streak reminders
Streaks are the reason to come back, but nothing reminds a player that today's is unsolved.
- Opt-in, asked once there's a streak worth keeping: "Remind me in the evening if I haven't
  solved today's puzzles."
- Web push needs a service worker. The decision against one (milestone 4) was about caching the
  app shell, which fights `firebase deploy`'s cache clearing; a worker that only handles push and
  has no fetch handler caches nothing, so that doesn't apply.
- Sent with Firebase Cloud Messaging (free). An hourly Cloud Scheduler run picks the players whose
  local evening it is and who haven't solved one of today's dailies; needs a
  `push_subscriptions` table (player, token, time zone).
- iPhone only allows web push for apps installed to the home screen (iOS 16.4 and later), so the
  prompt explains installing first there.
- The privacy page has to mention it.

## 6. Hints

**Status: done and deployed (2026-11-25; CI's deploy ran migration V12 on Neon).** As built:
- `POST /api/attempts/{id}/hint` with the board's digits (and notes). It lives under attempts
  because a hint is charged to one. Each call reveals the next step of the same move. The move is
  remembered in `attempts.last_hint` (migration V12), and a step is only charged the first time
  it's shown. The server sends only what the step reveals, so the answer can't be read from the
  network. Step 2 leaves the target out of the pattern (for a single, the pattern *is* the target).
- A board with wrong digits gets no hint: the client says "Fix the red digits first", and the
  server answers 422 with `wrongCells`.
- An elimination (X-Wing and the like) places no digit, so the server remembers the candidates
  of eliminations shown in full and applies them before looking for the next move. It also skips
  eliminations the player's notes already show. A test solves a whole puzzle by following hints.
  An elimination is shown in full in one tap (at step 1's price), and the digit it leads to starts
  at step 2, so the pair still takes three taps instead of six.
- Penalties (`attempt/Penalties.java`, `game/penalties.ts`): mistakes escalate, 5 s x mistakes²
  (5 s, 20 s, 45 s, 1:20…), so one slip is cheap but guessing isn't; hint steps 10, 20 and 60 s.
  Hint cost (changed 2026-11-25, branch `hint-fix`), charged once per step revealed:

  | Hint | Taps | Cost |
  |---|---|---|
  | A digit (any single, hidden or naked) | where, what, answer | 10 + 20 + 60 = 90 s |
  | An elimination (X-Wing, W-Wing, pointing pair…) | 1, shown in full | 10 s |
  | The digit right after an elimination | what, answer | 20 + 60 = 80 s |
  | k eliminations in a row, then a digit | k + 2 | 10k + 80 s |
  | A step already shown, or an elimination the notes already show | 0 | 0 s |

  So an elimination and the digit it leads to cost 90 s, the same as any other hint, where before
  they were charged as two full hints (180 s).
  `attempts.penalty_ms` is a generated column (5 s x mistakes² + `hint_penalty_ms`).
  Leaderboards, the daily cards' fastest times and profile best times rank the game's time plus
  it. The mistake part applies to past solves too. "Clean" now means no mistakes and no hints.
  Shown as "4:12 +40s" in the game header, on the result screen, on leaderboard rows (with a
  bulb icon for hinted solves), in the share line ("(2 hints)") and in a profile's recent solves.
- Hints are only offered on puzzles from the API. Restarting a non-daily clears them; a daily
  keeps them, as it keeps its time.
- A hint stays on screen until the player places a correct digit anywhere in the cells it points
  at. A wrong digit hides it until it's fixed. While the hinted move can still be made, the server
  keeps hinting it, even if a correct digit elsewhere made another move easier, so the next tap
  carries on to the next step instead of starting over.
- Recent shows the penalty in yellow next to each time: for an unfinished puzzle, its mistakes so
  far plus its hints.

The plan, as written before building it:

- The Hints button calls `POST /api/puzzles/{id}/hint` with the current grid. The server runs
  the human solver and returns the easiest next move: the strategy name ("Hidden single
  (box)") and the cell to highlight, or "this cell is wrong" if the grid has a mistake. The
  wording and highlighting come from "Explain a move" (After v1), built for the tutorial first.
- **Hints come in three steps (preferred, 2026-11-24).** Each tap gives away a little more:
  1. *Where*: "Look at row 4." The unit is shaded.
  2. *What to look for*: the technique described in plain words, with its name as a small label
     ("In this row, a digit has only one cell left where it can go." · *Hidden single*). The
     pattern and the blockers are highlighted, but not the target.
  3. *The answer*: the target cell and the full "Explain a move" sentence.
  Players who don't know the names still get a useful step 2, and seeing the same name next to the
  same idea teaches it. Tapping the label opens a "What's this?" card with the longer
  explanation. The wording is in `pidoku-frontend/src/game/strategies.ts` (`strategyInfo`: name,
  `lookFor`, `explanation`, done 2026-11-24). A test checks it covers every `StrategyEnum`
  constant and that `lookFor` never names the technique. The highlighting is what
  `Explanation` already returns, split across the steps.
- Each step counts in `attempts.hints_used`, so "told me the row" and "showed me the answer"
  aren't the same thing to calibration or the leaderboard.
- The server increments `attempts.hints_used`. How hints affect leaderboards is an open item
  (see "Open items").
- This shows the engine off better than anything else in the UI, since it explains moves the
  way the difficulty rating counts them.

## 7. Custom generation

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
  concurrency of about 40 a few of them would slow every other request. Build only this:
  - Cap concurrent generations per instance (about 2) with a semaphore; extra requests
    get a "busy, try again" response.
  - The timeout above, and sign-in required.
  - `max-instances=3` already caps the cost.
- Not built unless the above proves too little: a per-player rate limit, and running
  generation as a separate Cloud Run service with low concurrency. For a game among friends
  the semaphore is expected to be enough.
- The shared-candidate speedup and unit masks are done (about 44% faster). If players still wait too long, the
  next steps are listed under it in "Engine follow-ups".
- Generated puzzles are stored with `source = custom`, so they get share codes like any
  other puzzle but never enter the random pool.

## 8. Campaign

- A one-off script generates about 100 puzzles, sorts them by score and stores them as
  levels 1–100 (`source = campaign`).
- Difficulty rises gently, e.g. 10 Trivial → 15 Easy → 20 Medium → and so on.
- Once published, a level is never regenerated; new levels are added at the end.
- Frontend: a level map or grid with locks, and up to 3 stars per level (finished, no
  mistakes, under a target time). Progress synced to the server and cached in the browser.
- Target times come from real solve times, not from the hand-picked score: a percentile of
  recorded `active_ms` for puzzles of similar score, or the calibrated model if milestone 9
  has happened by then. Store them per level so they can be re-set without touching the
  puzzles.

## 9. Calibrating difficulty

The part of the project nobody else's Sudoku clone has: the difficulty model fitted to how long
real people took.

- Data: finished attempts, using `active_ms` (collected from milestone 4) rather than the
  wall-clock time, plus mistakes.
- Only dailies get several solves of the same puzzle; a random pool puzzle usually gets one.
  So don't fit on per-puzzle medians alone: divide each solve by that player's own typical
  time first (e.g. their median over all their solves), so one solve of a puzzle is still a
  usable data point and fast and slow players don't skew a puzzle that only one of them played.
- Fit a linear model predicting that normalised solve time from: hardest step
  (`max_step_cost`), top-10 step sum, dependency, bottlenecks (`bottleneck_steps`) and
  `clue_count`. Train on half the puzzles and test on the other half.
- Start when there are a few hundred solves across at least a few dozen puzzles. With five
  friends playing the three dailies that's about a month.
- Replace the hand-picked weights, re-set the score ranges, and record the fit (r²) in the
  README.
- Re-score stored puzzles: a one-off job recomputes `score` and `difficulty` for every
  puzzle with the new weights. Campaign levels keep their order and their puzzles even if
  their labels change; pool puzzles may move between difficulties.
- Dailies are generated 31 days ahead, so an engine or scoring change (the Trivial tune, new
  weights) reaches them a month later. That buffer is worth having while the job is new; once
  tuning starts, drop `pidoku.generator.daily-days-ahead` to about 7, or regenerate the future
  slots nobody has been served yet, as V5 did.

## Monetisation and analytics

**Status: planned.** The goal is to break even, not to make a profit. The stack is cheap by
design (Cloud Run scales to zero; Neon, Firebase Hosting and Auth have free tiers), so the main
rule is: don't add costs, or ads, before they're needed.

### Costs to watch
| Cost | Expected | Grows when |
|---|---|---|
| Domain | ~$10–20 a year | Fixed |
| Cloud Run (API and nightly generator) | Likely inside the free tier | Hundreds of daily players, or custom generation |
| Neon Postgres | Free tier | `attempts` and the pool outgrow it |
| Firebase Hosting and Auth | Free tier | Rarely at friend-group scale |

- Set a Google Cloud budget alert (e.g. $5 a month) so a surprise bill shows up early.
- Custom generation (milestone 7) is the one feature with real compute cost: Expert and
  Fiendish take 4–11 seconds each. Reusing a stored puzzle that matches the settings (by
  `strategy_uses`) keeps it down; also add a per-player rate limit.

### Revenue by milestone
Principles, for every step below:
- **Break even, not profit.** Monetise only when a cost is above the free tiers or one is coming.
- **Never sell an advantage.** Paying never changes ranking, hints or penalties.
- **Store the minimum.** No payer names, emails or amounts; supporting is opt-in, and deleting the
  account deletes it.

- **Now, before monetising:** the Google Cloud budget alert ($5 a month) and a monthly look at the
  cost table above. Ads stay out: they'd earn pennies at this scale and make the daily loop worse
  for the friends it's meant for.
- **v1: a tip jar.** ~~Ko-fi one-off tips with a supporter badge~~ built 2026-11-26 (below). Link
  it from `/me` and, once it's settled, the result screen. Ko-fi takes nothing from one-off tips
  (only the card or PayPal fee). Measure: tips cover the domain and the yearly bill.
- **Milestone 6 (hints): never sell an advantage.** Hints are free, and paying never changes the
  leaderboard. Hinted solves are marked or unranked (see the open item below).
- **Milestone 7 (custom generation): supporter perk.** The feature that costs money is the one
  supporters fund: e.g. everyone gets a few custom puzzles a day, supporters get more or get
  Fiendish, with a per-player rate limit for everyone. If monthly Ko-fi memberships come with it:
  each payment sets `players.supporter_until` about 35 days ahead (Ko-fi says nothing on a
  cancellation, so the badge just runs out). Renewals carry no code, so they'd be recognised by an
  HMAC (server-side secret) of the Ko-fi email, never the email itself. Check Ko-fi's fee on
  memberships first (believed 5% without Ko-fi Gold). If a perk ever becomes worth forging, move
  to Stripe, whose webhooks are signed.
- **Milestone 8 (campaign), only if still needed:** the first chapter free and later packs a
  small one-time purchase ($2–3), or one ad on the menu or result screen, never during play.
  Ads (very roughly $1–5 per 1,000 page views) only pay with thousands of regular players and
  bring a consent banner for EU and UK players and a privacy policy, so they're the last resort.
- **Review:** three months after the tip jar goes live, and again before milestone 7 ships.

**Supporter badge, as built (2026-11-26).** A player asks for a single-use code on `/me/support` (`PI-`
plus 8 characters, 30 days) and pastes it into their Ko-fi tip's message. Ko-fi's webhook
(`POST /api/kofi/webhook`) is checked against the verification token before anything else, and a
live code makes its player a supporter for good (`players.supporter_since`; `supporter_until`
stays null). The minimum tip is set on Ko-fi; the API never reads the amount. Only Ko-fi's transaction id is stored, which makes a resent webhook a no-op and lets
a tip without a code be linked by hand (docs/DEVELOPMENT.md, "Ko-fi supporters"). Friend codes
weren't used for this: they add friends with nothing to accept, and Ko-fi can show messages
publicly. Ko-fi doesn't sign its webhooks, so a leaked token could hand out badges, nothing more.

### Analytics
Two layers:
1. **Gameplay data in our own Postgres.** `attempts` already records times, active time,
   mistakes and hints. Add a small `events` table (player_id, type, puzzle_id, a JSONB detail,
   created_at) for what `attempts` doesn't cover. This is also the input for calibration
   (milestone 9).
2. **A cookieless page-view counter** (Cloudflare Web Analytics, GoatCounter or self-hosted
   Umami) for visits, referrers and devices. Cookieless means no consent banner; Google
   Analytics is skipped because it needs one and answers fewer of our questions.

Plus Sentry's free tier for frontend errors, and Cloud Run's logs for API errors and latency.

What to collect, most useful first:

| Question | Data | Source |
|---|---|---|
| Do people come back? | Daily active players; return after 1, 7 and 30 days; streak lengths | `attempts` |
| Is each difficulty right? | Finish rate, median active time and mistakes per difficulty, against the engine score | `attempts` and `puzzles` |
| Where do people quit? | Started but unfinished; cells filled when they stopped | `attempts` and an `abandoned` event |
| Does sharing bring players? | Share taps; new players arriving from a share link | `events` and a `?ref=share` parameter |
| Does sign-in work? | Share of anonymous players who link Google; friends added | `players` and `friendships` |
| Which features get used? | Notes mode, hints, undo | `events`, sampled if it gets large |
| Where does traffic come from? | Visits, referrers, device and screen size | Page-view counter |

Rules:
- Analytics rows carry `player_id` only, never emails or display names.
- Don't log every keystroke; record an event only when it answers a question above.
- Keep the privacy page (`/privacy`) up to date: it lists the game events (updated 2026-11-21).
- First steps: the budget alert and share-link tracking. They show whether monetising is
  needed at all and whether the game is spreading.
- Done 2026-11-21: the `events` table, share-link tracking and the `abandoned` event (see "After
  v1"). Still to do: the page-view counter and Sentry.

## Showcase

What someone looking at the repository for two minutes sees. Not a milestone; items marked v1
are done before v1 is called done.

- ~~**README (v1).**~~ Done 2026-11-20: the live link, CI badge, a screenshot
  (`docs/images/screenshot.png`), an architecture diagram (Mermaid) and the engineering worth
  reading about, with its numbers. Retake the screenshot when the home screen changes.
- ~~**CI (v1).**~~ Done: CI runs on GitHub Actions (section 2), with its badge in the README.
- ~~**End-to-end tests.**~~ Done 2026-11-21: Playwright, desktop and phone, in CI on every pull
  request (see "After v1").
- **Calibration write-up (after milestone 9).** A short section or page: the model, the data,
  the fit, a chart of predicted against actual solve time.

## Frontend overview

- The API client has replaced the bundled puzzles as the main source; the bundled ones
  (`src/puzzles`, engine JSON files) are the offline fallback. Loading engine JSON from the home
  screen was removed on 2026-11-20; dropping a file into `src/puzzles` is the way to try one.
- Home screen today: Today's dailies (each card with its hardest technique, who has solved it and
  the fastest time) with the streak, Random puzzle, Recent. How to play (tutorial), Leaderboards
  (any date), Friends and You (5). To come: Generate (7), Campaign (8).
- Routes: `/`, `/p/{shareCode}` and `/local/{id}` (milestone 4); `/leaderboard/{date}`, `/me` and
  `/f/{friendCode}` (milestone 5); `/privacy` and `/tutorial` (after v1); `/me/stats` (stats page);
  `/friends`, `/me/look`, `/me/support` and `/me/account` (the split of You & friends,
  2026-11-26). Campaign levels will add theirs.

## Engine follow-ups

- ~~Add an `engine_version` constant to the engine.~~ Done: `PidokuEngine.VERSION`, from the
  Gradle project version, and `PidokuPuzzle.getEngineVersion()`.
- ~~Make generation thread-safe.~~ Done: `PidokuBuilder.standard(rules)` is shared, and
  `generate(profile, seed)` keeps its random state local to the call.
- ~~Stop Easy, Medium and Hard overlapping.~~ Done in engine 1.2.0 (migration V10 regenerated
  the future dailies): score windows 25–40 / 45–69 / 70–120, targets 32 / 57 / 95, Medium capped
  at 36 clues (Easy needs 38+), Hard at 36. Why: the 2026-11-23 Medium (39 clues) was almost the
  same puzzle as that day's Easy (40), and all four players who solved both were faster on it.
  An early fit on 29 solves of 14 dailies (11-19 to 11-24) found clue count tracked solve times
  better than dependency within Easy and Medium, but under leave-one-puzzle-out no rewrite of the
  score beat the current one, so the weights are unchanged until milestone 9 has more data.
- Tune the Trivial target, which is too easy at 65 clues.
- **Shared-candidate speedup** (done 2026-11-26, branch `engine-speedup`; no output change, so no
  engine version bump).
  - *The problem:* to find the next move, `HumanPidokuSolver.nextMove` runs every strategy over the
    board, and each one kept asking `SearchUtils.isCandidate` whether a digit can go in a cell, which
    allocated a `BoardPosition` and re-ran `SolverSearchState.canPlace` every time.
  - *The fix:* `SolverSearchState` keeps a candidate bitmask per cell (`candidateMask(row, col)`),
    rebuilt lazily on the first read after a `place` or `remove`; an `eliminate` clears its own bit.
    `isCandidate`/`getCandidates` read it, so every strategy in a step shares one computation.
    `getCandidates` keeps the rules' digit order (3, 1, 4, 5, 9, 2, 6), which hidden subsets'
    elimination order depends on. The backtracker's `findNextCell` deliberately doesn't use the
    cache (the board changes at every node, and a full rebuild would lose its early exit); it
    computes each cell's mask directly, without allocating.
  - *Proof of no behaviour change:* `GoldenTest` compares against `src/test/resources/golden/`:
    generated puzzles, scores and solve steps for every premade profile (seeds 1 and 2), and a hash per
    strategy of everything `findAll` returns (moves, elimination order, reasons) along the solves of
    200 seeded boards, plus the chosen moves and their explanations. The strategy hashes matter:
    sorting `getCandidates` changed no generated puzzle but failed five of them. Regenerate with
    `./gradlew :pidoku-engine:updateGolden` only when a change is meant to alter outputs (the twin
    X-Wing will). `SolverSearchStateTest` checks the cache against a fresh computation after random
    place/remove/eliminate sequences.
  - *Measured* with `./gradlew :pidoku-engine:benchmark` (not in CI), on one 16-thread Linux machine,
    seeds 1..5, mean ms per puzzle, two runs each:

    | | Trivial | Easy | Medium | Hard | Expert | Fiendish | all six |
    |---|---|---|---|---|---|---|---|
    | before | 27–31 | 31–32 | 62–66 | 157–161 | 3,680–3,860 | 4,940–5,200 | 8,900–9,300 |
    | after | 24–27 | 25–29 | 51–61 | 118–130 | 2,780–2,980 | 3,710–3,910 | 6,700–7,100 |

    About 24% faster overall. Per call: the human solver 690–730 → 450–490 µs, and the uniqueness
    check 150–160 → 110 µs, because `findNextCell` no longer allocates.
  - *Unit masks* (2026-11-26, after the above): each row, column and box keeps a mask of the digits
    it can still take (count below frequency, and only the completing digit when one cell is left),
    updated on `place`/`remove`. `canPlace` and a cell's candidates are now three ANDs, for both
    solvers. Outputs unchanged (`GoldenTest`); `SolverSearchStateTest` checks `canPlace` against the
    old count-based rule. The last-cell rule turns out to be implied by the counts, since no count
    can exceed its frequency, but it's kept so the behaviour is exactly the old one.

    | | Trivial | Easy | Medium | Hard | Expert | Fiendish | all six |
    |---|---|---|---|---|---|---|---|
    | unit masks | 24–28 | 23 | 40–41 | 89 | 2,100–2,170 | 2,800–2,840 | 5,100–5,200 |

    That's about 25% faster than the cache alone and about 44% faster than the original engine. The
    uniqueness check went from 110 to 55 µs per call, and the human solver from 450–490 to 430 µs.
  - *Tried and rejected* (2026-11-26, measured, not kept):
    - Dropping the per-candidate uniqueness check: a board the human solver finishes is unique, so
      the check adds no information, but it rejects ambiguous boards in about 110 µs where the human
      solver only gives up after every strategy has failed. Generation took 13.2–13.6 s, twice as
      long. The backtracker is a cheap filter in front of the expensive solver.
    - Stopping the human solve at the profile's tier ceiling: exactly the same outputs, but within
      noise (Medium about 10% faster, the rest unchanged), because the human solver only runs on
      boards that already passed the uniqueness check. Not worth a second solve path.
  - *Where the time goes now:* the human solver is about 70% of generation for Hard and up, and the
    uniqueness check about 30%. Next steps if custom generation needs more:
    1. Fewer allocations in the strategies (reuse the empty-cell lists per step; masks instead of
       `List<Integer>` in the subset strategies, with the golden test guarding ordering).
    2. For the backtracker, updating the 20 peers' masks on `place`/`remove` instead of recomputing in
       `findNextCell`. Less valuable now that a cell's mask is three ANDs.
- **Techniques only Pidoku has.** The doubled digits allow deductions classic Sudoku doesn't.
  The engine already has one: a unit with exactly two places left for its two 5s (the doubled
  hidden single, costed ×1.5). X-Wing and W-Wing only handle digits that appear once. A counting
  fish works for doubled digits: if two rows still need four 5s between them and those 5s can
  only go in two columns, each of those columns gets both its 5s in those rows, so 5 comes out
  of every other cell in those columns. In general: when the copies a set of rows still needs
  equals what a set of columns can still hold. Name them (a "twin X-Wing"), give them costs and
  run them through `StrategySoundnessTest`. They feed the tutorial, hints, custom generation
  ("must need a twin X-Wing") and the README: new solving techniques for a new variant.

## Open items

- Calibration: none of the scoring weights have been checked against real players.
- The new strategy names have only been checked on the puzzle cards, not in the game screen.
- ~~A daily can be a puzzle someone has already played.~~ Fixed: dailies are generated fresh
  (milestone 4). Today's and tomorrow's dailies on the day V5 runs may still be old pool puzzles.
- ~~**Hints and leaderboards**~~ and ~~**mistakes on dailies**~~: decided and built 2026-11-25
  (milestone 6). Time penalties on the Fastest board: 5 s x mistakes² (escalating, chosen over a
  flat 10 s that still made guessing pay on hard puzzles), and hint steps 1 / 2 / 3
  at +10 / +20 / +60 s. Every solve still ranks and keeps the streak. Auto notes stays a marker
  only. The options that were weighed:
- **Hints and leaderboards** (was: decide in milestone 6). Options:
  - a time penalty per hint step on the Fastest board (e.g. +30 s, shown like "4:12 +30s");
  - the solve still counts for the streak but is left off the leaderboard;
  - no hints on dailies at all (hints only on random, shared and custom puzzles);
  - something else (e.g. ranked but marked with a hint icon, or a penalty for step 3 only).
  Decide it with the mistakes question below: both are about how assisted solves rank, along with
  auto notes (`auto_notes_used`).
- **Mistakes on dailies.** Wrong digits always show in red and the Fastest board ignores
  mistakes, so tapping digits until one isn't red beats working a cell out. Decide between a
  time penalty per mistake on the Fastest board (e.g. +10 s, shown as "4:12 +20s") and a limit
  of three mistakes per daily. Leaning towards the penalty: it keeps every solve and doesn't
  end anyone's streak; a limit could be offered later as an opt-in "hardcore" mode.
- **Privacy page.** Done (2026-11-20): `/privacy`, linked from the home screen, `/me` and the
  Google sign-in button, with "Delete my account" on `/me` (`DELETE /api/players/me` plus the
  Firebase account). Contact: pidoku@googlegroups.com. Left to do by hand: add
  `https://pidoku.web.app/privacy` to the Google OAuth consent screen.
