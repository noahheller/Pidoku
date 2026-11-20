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

   Next, before milestone 6, in this order (see "After v1"):
   - Analytics events first, so each change below can be measured against what came before.
   - An "explain a move" layer, then a tutorial for first-time players built on it.
   - Auto notes on Expert and Fiendish puzzles.
   - Playwright end-to-end tests, written alongside the above.
   - Progress sync between devices.
   - Recents in place of "Your puzzles".

6. Hints (reusing the "explain a move" layer).

   Then, before milestone 7: leaderboards for any shared puzzle (challenge links), a player stats
   page, and opt-in streak reminders.

7. Custom generation: players build their own difficulty.
8. Campaign mode, end to end.
9. Calibrate difficulty once real data exists. (Collecting the data starts in milestone 4.)

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
| `PUT /api/attempts/{id}/progress` | The board, notes, mistakes and timer of an unfinished attempt, so it continues on another device | after v1 |
| `GET /api/players/me/recent` | The player's last few puzzles, unfinished first, for Recents | after v1 |
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
  - Not used: Spring AOT (`processAot`). It fixes profiles at build time, which clashes with one
    image serving both the API and the `generator` profile. GraalVM native only if cold starts
    are still too slow once measured on Cloud Run.
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
  the players either side, and "photo finish" for times under a second apart. Two extra columns in
  the same ranking query: first to finish (late solves excluded, their times being the client's
  claim) and the fastest clean solve, both returned even when beyond the top 50. Friends' current
  streaks come from one query for all friends when `/me` opens, as of the viewer's date (it can be a
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
- **`/me` ("You & friends")**: the name (the place to change it that leaderboards needed), the
  friend code with Copy link / Share, adding by code, the friend list, and the account.
- **Google sign-in** is a popup (`linkWithPopup`), which keeps the uid. If that Google account is
  already a player, this browser switches to it (`signInWithCredential`): unsent solves are sent
  first, and the cached streak and dismissed prompts are cleared. Signing out gives the browser a
  fresh anonymous player. Asked for under a daily's result once the streak is 3 days ("Not now"
  is remembered), and always available on `/me`.

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

### Analytics first
Do the first analytics steps (see "Monetisation and analytics") before the rest of this list:
the `events` table, `?ref=share` on share links and the `abandoned` event. Each piece below
changes how people play or how fast they solve, and its effect can only be measured if the data
starts before it ships (for example, finish rate on Hard before and after the tutorial). Record
tutorial completion, auto notes and (milestone 6) hints as they arrive, so calibration
(milestone 9) can tell assisted solves from unassisted ones.

### Explain a move
The tutorial and hints need the same thing: a solving step turned into something a player can
follow. Build it once, before either.
- The engine already finds each step (`Move`: the strategy, the cells it places or clears). Add
  what a player needs: the cells to highlight (the pattern and its target) and one plain sentence,
  e.g. "Row 4 needs two more 5s, and only these two cells can take a 5."
- The doubled digits need their own wording; they're what the tutorial is mostly about.
- Player-facing technique names already exist in `pidoku-frontend/src/game/strategies.ts`; keep
  one source of truth (the engine's names sent with the step, or that file).
- Used by the tutorial, hints (milestone 6) and later custom generation ("must need an X-Wing").

### Tutorial
The doubled 1s and 5s are the whole twist, and also the part new players get wrong. Today the
rule is a line of text under the number pad, easy to miss on a phone.
- Shown the first time someone opens a puzzle (remembered in this browser), and always
  reachable from the home screen ("How to play").
- A short guided puzzle (a small or Trivial board) with step-by-step prompts that point at
  cells, worded by "Explain a move": a normal single, then a row that already holds one 5 and
  needs its second, then a note being cleared automatically once a unit holds both copies.
- Explains the controls along the way: notes mode, undo and erase, and that wrong digits show
  in red and count as mistakes.
- Not timed and not recorded as an attempt.

### Auto notes
A button that pencils every candidate into every empty cell, so Expert and Fiendish players
don't spend minutes on bookkeeping before the real solving starts.
- Only on **Expert and Fiendish** puzzles; easier ones are meant to be solved without it.
- Candidates follow the Pidoku rule: a digit stays a candidate in a unit until the unit holds as
  many copies as π allows (two for 1 and 5).
- Fills from the board as it is (filled cells and the notes the player cleared are respected, or
  everything is refilled: decide when building it), and is one step on the undo stack.
- Available to everyone, so it doesn't change who can rank. Record its use
  (`attempts.auto_notes_used` or an `events` row) because it changes solve times and the
  calibration (milestone 9) has to account for it.

### Playwright end-to-end tests
The frontend's unit tests cover the game logic and the API client, and the API's tests cover the
endpoints, but nothing checks the pieces together. Add Playwright tests against a local API
(Testcontainers Postgres) and the Vite build:
- Open a daily, press Start, solve it from the solution, and see the result screen with the
  server's time, the streak and the share line; "Next" opens the next daily.
- The clock pauses when going back to Puzzles and carries on when the puzzle is reopened.
- Leaderboard: the solve appears on Everyone and, after adding a friend by code, on Friends; the
  date picker moves between days.
- The home screen's cards show the solve ("1 friend solved") for a friend.
- Run them on a phone-sized viewport as well as desktop, and in CI on every pull request.
- Firebase sign-in in tests: use the Firebase Auth emulator, or a test-only token the local API
  accepts, so the tests don't depend on the real project. (Checked by hand on 2026-11-20 with
  headless Chromium, Firebase blocked, against a local API on a throwaway Postgres; the
  Playwright tests make that repeatable.)

### Progress sync between devices
A game's board lives in one browser's localStorage. The attempt (and its clock) follows the
player to another device, but the board doesn't: a daily started on a phone opens on a laptop
with an empty board and the game's timer at 0:00, so its ranked time only counts the second
device. The Google sign-in prompt promises play on other devices, so this is owed.
- Save the unfinished attempt's board, notes, mistakes and game timer to the server
  (`PUT /api/attempts/{id}/progress`): every few moves (debounced, about every 10 seconds of
  play) and when the page is hidden (`fetch` with `keepalive`, so it survives closing the tab).
  New columns on `attempts` (the board and notes as JSONB, `progress_updated_at`).
- Opening a puzzle with an unfinished attempt takes whichever copy is newer, the server's or
  this browser's. Last write wins; two devices playing the same puzzle at once isn't worth more.
- The ranked time is then the timer carried across devices, still capped at the server's
  wall-clock time.
- Campaign (milestone 8) needs the same thing ("progress synced to the server"), so it's built
  once here.

### Recents
"Your puzzles" keeps every random puzzle ever played, in this browser only, and never shrinks.
With "Load a puzzle" gone, it's really a history.
- Rename it **Recent**: the last 10 or so puzzles, unfinished first ("Continue"), then the latest
  solves with their times. Older ones drop off (their saved progress is cleared with them).
- Signed in, it comes from the server's attempts (`GET /api/players/me/recent`), so with progress
  sync it's the same list on every device; signed out, from localStorage as now.
- Dailies can appear too, which also gives a way back to a daily started earlier in the day.

### Leaderboards for any shared puzzle (challenge links)
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

- The Hints button calls `POST /api/puzzles/{id}/hint` with the current grid. The server runs
  the human solver and returns the easiest next move: the strategy name ("Hidden single
  (box)") and the cell to highlight, or "this cell is wrong" if the grid has a mistake. The
  wording and highlighting come from "Explain a move" (After v1), built for the tutorial first.
- Consider hints in steps: first "look at row 4", then the technique, then the exact cell. Each
  step gives away less of the puzzle, and the steps show the engine off better than jumping
  straight to the answer.
- The server increments `attempts.hints_used`. To decide: whether a solve with hints ranks on
  the leaderboard, is marked there, or is left off.
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
- The shared-candidate solver speedup becomes worth doing here, since players wait on it.
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
- **Now to v1: nothing.** Ads would earn pennies at this scale and make the daily loop worse
  for the friends it's meant for.
- **v1: a tip jar.** A "Support Pidoku" link (Ko-fi, Buy Me a Coffee or GitHub Sponsors) on the
  result screen or an about page. No payment code; a few supporters cover the domain and a
  small bill.
- **Milestone 6 (hints): never sell an advantage.** Hints are free, and paying never changes the
  leaderboard. Hinted solves are marked or unranked (see the open item below).
- **Milestone 7 (custom generation): supporter perk.** The feature that costs money is the one
  supporters fund: e.g. everyone gets a few custom puzzles a day, supporters get more or get
  Fiendish. Stripe or Ko-fi memberships, with a `supporter` flag on `players`.
- **Milestone 8 (campaign), only if still needed:** the first chapter free and later packs a
  small one-time purchase ($2–3), or one ad on the menu or result screen, never during play.
  Ads (very roughly $1–5 per 1,000 page views) only pay with thousands of regular players and
  bring a consent banner for EU and UK players and a privacy policy, so they're the last resort.

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
- Keep the privacy page (`/privacy`, done) up to date: it says there are no analytics, so the
  first analytics change must update it.
- First steps: the budget alert and share-link tracking. They show whether monetising is
  needed at all and whether the game is spreading.
- Then the `events` table and the `abandoned` event, before the tutorial, auto notes and hints
  ship (see "After v1": "Analytics first").

## Showcase

What someone looking at the repository for two minutes sees. Not a milestone; items marked v1
are done before v1 is called done.

- ~~**README (v1).**~~ Done 2026-11-20: the live link, CI badge, a screenshot
  (`docs/images/screenshot.png`), an architecture diagram (Mermaid) and the engineering worth
  reading about, with its numbers. Retake the screenshot when the home screen changes.
- ~~**CI (v1).**~~ Done: CI runs on GitHub Actions (section 2), with its badge in the README.
- **End-to-end tests.** Playwright, planned in "After v1".
- **Calibration write-up (after milestone 9).** A short section or page: the model, the data,
  the fit, a chart of predicted against actual solve time.

## Frontend overview

- The API client has replaced the bundled puzzles as the main source; the bundled ones
  (`src/puzzles`, engine JSON files) are the offline fallback. Loading engine JSON from the home
  screen was removed on 2026-11-20; dropping a file into `src/puzzles` is the way to try one.
- Home screen today: Today's dailies (each card with its hardest technique, who has solved it and
  the fastest time) with the streak, Random puzzle, Your puzzles (to become Recent). Leaderboards
  (any date) and You & friends (5). To come: How to play (tutorial), Generate (7), Campaign (8).
- Routes: `/`, `/p/{shareCode}` and `/local/{id}` (milestone 4); `/leaderboard/{date}`, `/me` and
  `/f/{friendCode}` (milestone 5). Campaign levels will add theirs.

## Engine follow-ups

- ~~Add an `engine_version` constant to the engine.~~ Done: `PidokuEngine.VERSION`, from the
  Gradle project version, and `PidokuPuzzle.getEngineVersion()`.
- ~~Make generation thread-safe.~~ Done: `PidokuBuilder.standard(rules)` is shared, and
  `generate(profile, seed)` keeps its random state local to the call.
- Tune the Trivial target, which is too easy at 65 clues.
- Shared-candidate speedup (needed for custom generation).
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
- Whether a solve that used hints ranks on the leaderboard (decide in milestone 6).
- **Mistakes on dailies.** Wrong digits always show in red and the Fastest board ignores
  mistakes, so tapping digits until one isn't red beats working a cell out. Decide between a
  time penalty per mistake on the Fastest board (e.g. +10 s, shown as "4:12 +20s") and a limit
  of three mistakes per daily. Leaning towards the penalty: it keeps every solve and doesn't
  end anyone's streak; a limit could be offered later as an opt-in "hardcore" mode.
- **Privacy page.** Done (2026-11-20): `/privacy`, linked from the home screen, `/me` and the
  Google sign-in button, with "Delete my account" on `/me` (`DELETE /api/players/me` plus the
  Firebase account). Contact: pidoku@googlegroups.com. Left to do by hand: add
  `https://pidoku.web.app/privacy` to the Google OAuth consent screen.
