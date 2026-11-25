# Development guide

How to run, test, build and deploy Pidoku. For an overview of the project, see the
[README](../README.md); for the REST endpoints, see [API.md](API.md).

## Running locally

Requirements: JDK 25, Node 22+, and Docker (for API tests and the container image).

The quickest way to play locally, once `pidoku-api/.env` points at the Neon `dev` branch (see
[Neon dev branch](#neon-dev-branch)):

```bash
scripts/dev.sh              # API on :8080 + frontend on http://localhost:5173; Ctrl+C stops both
scripts/dev.sh --generate   # first fills the dev database with puzzles, then the same
```

Or run the pieces yourself:

```bash
./gradlew build                 # builds and tests the engine and API (API tests use Testcontainers)
./gradlew :pidoku-api:bootRun   # runs the API
cd pidoku-frontend && npm install && npm run dev
cd pidoku-frontend && npm test  # frontend game-logic tests (Vitest)
cd pidoku-frontend && npm run e2e  # end-to-end tests (Playwright; see pidoku-frontend/e2e/README.md)
```

The frontend gets its puzzles from the API (the Vite dev server proxies `/api` to `:8080`). When
the API can't be reached, it offers the puzzles bundled in `pidoku-frontend/src/puzzles/*.json`
instead: the `*-stats.json` files the engine's `Main` writes to `output/`. Copy a new one in as
`NN-difficulty.json` to try it. Game progress is saved in localStorage.

The API reads its database settings from environment variables, or from a gitignored
`pidoku-api/.env` file in `.properties` format:

```properties
DATABASE_URL=jdbc:postgresql://<neon-pooled-host>/pidoku?sslmode=require
DATABASE_DIRECT_URL=jdbc:postgresql://<neon-direct-host>/pidoku?sslmode=require
DATABASE_USER=...
DATABASE_PASSWORD=...
```

The app's queries use the pooled connection (`DATABASE_URL`, host ending in `-pooler`). Flyway
migrations use the direct one (`DATABASE_DIRECT_URL`, the same host without `-pooler`): the
migration lock needs one connection for the whole migration, and the pooler only lends a
connection for a single transaction. If `DATABASE_DIRECT_URL` isn't set, Flyway falls back to
`DATABASE_URL`.

### Generator job

Puzzles are generated ahead of time and stored, not generated per request. The generator job
fills the daily puzzles for the next `pidoku.generator.daily-days-ahead` days with newly generated
puzzles, then tops up the random pool (`pidoku.generator.pool-size-per-difficulty` puzzles of
each difficulty that even its most active player hasn't started, so the pool grows as people play).
Dailies never come from the pool, so no one can have played one already. It's the same
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
   DATABASE_DIRECT_URL=jdbc:postgresql://<host>.<region>.aws.neon.tech/<db>?sslmode=require
   DATABASE_USER=<user>
   DATABASE_PASSWORD=<password>
   ```

To start over on `dev`, for example after a migration you were still editing has run there, use
**Reset from parent** on the branch. It goes back to a copy of `main`, and the next startup
re-applies your migrations. Run the generator job afterwards if you need puzzles in it.

### Container image

The API and the generator job ship as one image, built with Jib. The job is the same image run
with `SPRING_PROFILES_ACTIVE=generator`.

```bash
scripts/build-image.sh            # builds pidoku-api:latest into the local Docker daemon
scripts/build-image.sh IMAGE      # also pushes it, e.g. <region>-docker.pkg.dev/<project>/<repo>/pidoku-api:<sha>
```

The image includes a JVM AOT cache (JDK 25), which stores the classes the JVM loaded and linked
during a training run, so cold starts on Cloud Run skip most of that work. The script builds the
image, runs it against a throwaway Postgres (seeded with a few generated puzzles) while it serves a
round of typical requests, records the cache when it stops, rebuilds the image with the cache, then
checks the result with `-XX:AOTMode=on`, which fails rather than silently ignoring a cache that
doesn't match.

The Cloud Run service also runs Spring AOT code (`processAot`, built with the `api` profile) and
touches no database while starting: it doesn't migrate or check the schema, and it opens its first
connection on a background thread. It gets this by setting
`JAVA_TOOL_OPTIONS=-Dspring.aot.enabled=true`; the generator job, tests and `scripts/dev.sh` run
without it. Measured locally on one CPU against a local Postgres: ready in about 3.5 s instead of
5 s, and the first request answered after about 4 s instead of 6 s.

Things that keep the cache valid (see `pidoku-api/build.gradle`):
- The base image is pinned by digest. A cache only works with the exact JDK build that made it.
- Jib's `packaged` mode puts the app on the classpath as a JAR. The cache can't use class directories.
- The JVM flags (Serial GC, compact object headers) are fixed in the image, so the training run and
  production always match. Changing them means rebuilding with the script.

`./gradlew :pidoku-api:jibDockerBuild` still builds an image without a cache, which is quicker for
trying out changes.

## Sign-in

Every visitor is signed in anonymously with Firebase Authentication: no sign-up, just a uid that
stays with the browser. The frontend sends the account's ID token to the API, which checks it
against Google's public keys and creates the `players` row on first use. Opening a puzzle from the
API starts an attempt (for a daily, pressing Start does) and solving it completes it, so solve
times are measured by the server.

The Firebase project is set up for this (to repeat it in another project):

1. Enable the **Anonymous** provider: Firebase console → Authentication → Sign-in method.
2. Register a web app and print its config:

   ```bash
   firebase apps:create WEB pidoku-web --project pidoku
   firebase apps:sdkconfig WEB --project pidoku
   ```

3. Put that config in `pidoku-frontend/src/api/firebaseConfig.ts`. It isn't secret. Set to
   `null`, the game plays signed out and nothing is recorded.

The API needs no credentials for this. It accepts tokens issued for the Firebase project in
`pidoku.firebase.project-id` (`application.properties`).

## Deploying by hand

CI/CD normally does this: GitHub Actions (`.github/workflows/ci.yml`) tests every pull request
and deploys every merge to main. To deploy by hand anyway, run this from the repository root with
`gcloud` and `firebase` signed in:

```bash
./gradlew build && (cd pidoku-frontend && npm run lint && npm test)

IMAGE=us-east4-docker.pkg.dev/pidoku/pidoku-repository/pidoku-api:$(git rev-parse --short HEAD)
scripts/build-image.sh "$IMAGE"
gcloud run jobs update pidoku-api --project pidoku --region us-east4 --image "$IMAGE"
gcloud run jobs execute pidoku-api --project pidoku --region us-east4 \
    --update-env-vars SPRING_PROFILES_ACTIVE=migrate --wait   # database migrations
gcloud run deploy pidoku-api-service --project pidoku --region us-east4 --image "$IMAGE" \
    --execution-environment gen2 --update-env-vars JAVA_TOOL_OPTIONS=-Dspring.aot.enabled=true \
    --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=1,timeoutSeconds=1,failureThreshold=120 \
    --readiness-probe=""   # the same settings as CI; see the comments in ci.yml
firebase deploy --only hosting --project pidoku   # builds the frontend first
```

Migrations go first, because the service doesn't run them on startup. Then the API, because the
frontend may call endpoints the old API doesn't have.
