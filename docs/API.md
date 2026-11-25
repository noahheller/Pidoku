# API reference

The app calls the `/api` endpoints on its own origin: Firebase Hosting rewrites `/api/**` to the
Cloud Run service, so there's no CORS. The `/actuator` health checks are on the service itself.

| Endpoint | Returns |
|---|---|
| `GET /api/puzzles/random?difficulty=hard` | A random puzzle from the pool. Signed in, one the player hasn't started yet; once they've started them all, the one they started longest ago with `"repeat": true` |
| `GET /api/puzzles/daily/{date}` | The daily puzzles (easy, medium and hard, easiest first) for a date such as `2026-11-17`. The frontend asks for the player's local date. Only dates that are today somewhere in the world are served; a complete day is cached by Firebase's CDN |
| `GET /api/puzzles/{shareCode}` | The puzzle behind a share code (case-insensitive). The frontend opens it at `/p/{shareCode}`. Cached by the CDN |
| `GET /api/puzzles/daily/{date}/leaderboard?difficulty=hard` | The fastest solves of one daily: each player's first solve, by the game's timer. `&clean=true` ranks only solves with no mistakes; `&friends=true` (🔒) only the player and their friends. Sign-in optional otherwise; with it, the player's own entry is marked |
| `GET /api/puzzles/daily/{date}/summary` | For the home screen's cards: how many have solved each of the date's dailies and the fastest time. Signed in, also how many of the player's friends have and the fastest among them |
| `GET /api/players/me` 🔒 | The signed-in player, created on first use |
| `PATCH /api/players/me` 🔒 | `{"displayName": "Ada"}` → the player, renamed. Trimmed, whitespace collapsed, 1–40 characters, no control characters; 400 otherwise |
| `DELETE /api/players/me` 🔒 | Deletes the player with their attempts and friendships (both directions); 204, also when there was no player. The app deletes the Firebase account itself |
| `GET /api/friends?today=2026-11-17` 🔒, `POST /api/friends`, `DELETE /api/friends/{id}` | The player's friends (with their current streaks), add one by friend code (both become friends), remove one |
| `GET /api/friends/{id}?today=2026-11-17` 🔒 | One friend's streaks, dailies solved, clean rate, best times and last week of dailies. 404 unless you're friends |
| `GET /api/players/me/streak?today=2026-11-17` 🔒 | `{"current": 3, "best": 5, "solvedToday": true}`: consecutive dates with a solved daily, as of the player's local date. 400 if that date isn't today anywhere |
| `POST /api/attempts` 🔒 | `{"puzzleId": 12}` → the player's unfinished attempt at the puzzle, started now if there isn't one. With `"restart": true` its clock starts again from now |
| `POST /api/attempts/{id}/complete` 🔒 | `{"grid": [...], "mistakes": 2, "activeMs": 251000}` → the attempt, completed, with the server-measured `elapsedMs`. Leaderboards rank `activeMs` (the game's timer, paused while the player is away; capped at `elapsedMs`), or `elapsedMs` when a solve has none. A completion sent late adds `"lateByMs"`, which is taken off the time and marks it `late`. 422 if the grid isn't the solution |
| `GET /actuator/health` | Health check |
| `GET /actuator/health/liveness` | Liveness only, no database check (the frontend's warm-up ping) |

🔒 needs `Authorization: Bearer <Firebase ID token>`; without one it's a 401. See [Sign-in](DEVELOPMENT.md#sign-in).

Difficulties are `trivial`, `easy`, `medium`, `hard`, `expert` and `fiendish`. Puzzles come back
with flat, row-major `givens` and `solution` arrays (0 is an empty cell), plus `score`,
`dependency`, `hardestStrategy` and `dailyDate` (null unless it's a daily). Errors are [problem details](https://www.rfc-editor.org/rfc/rfc9457):
404 for an unknown share code, 503 when the generator job hasn't produced a puzzle yet, 400 for a
bad request.
