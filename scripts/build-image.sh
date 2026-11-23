#!/usr/bin/env bash
# Builds the pidoku-api container image with a JVM AOT cache (JDK 25, JEP 483/514/515).
#
# Usage:
#   scripts/build-image.sh            builds pidoku-api:latest into the local Docker daemon
#   scripts/build-image.sh IMAGE      also pushes it as IMAGE, e.g.
#                                     us-east4-docker.pkg.dev/<project>/<repo>/pidoku-api:<git-sha>
#
# The AOT cache holds the classes the JVM loaded and linked during a training run, plus profiles of
# the methods it ran, so a cold start skips most of that work. A cache only works with the exact JDK
# build, JVM flags and classpath that created it, so the training run has to happen inside the image
# itself, which Jib can't do (it assembles layers and never runs the app). Hence two passes:
#
#   1. Jib builds the image without a cache.
#   2. That image is started against a throwaway Postgres (migrated, with a few generated puzzles)
#      the way the Cloud Run service runs it (Spring AOT on), serves a round of typical requests, and
#      is stopped. The JVM writes the cache on exit, so it covers handling requests, not just startup.
#   3. Jib builds the image again with the cache added. The layers are reproducible, so the
#      classpath JARs are byte-identical (same paths, sizes and timestamps) to the training run.
#   4. The final image is started once with -XX:AOTMode=on, which fails if the cache can't be used.
#
# Requires Docker and curl. Pushing uses Docker's credentials (gcloud auth configure-docker <region>-docker.pkg.dev).
set -euo pipefail

cd "$(dirname "$0")/.."

push_target=${1:-}
train_image=pidoku-api:aot-training
final_image=pidoku-api:latest
aot_dir=pidoku-api/build/aot
network=pidoku-aot-$$
db=pidoku-aot-db-$$
trainer=pidoku-aot-train-$$

# How the Cloud Run service runs the image: Spring AOT's generated code, which also turns on the
# "api" profile it was built with (see pidoku-api/build.gradle). The service sets this through
# JAVA_TOOL_OPTIONS; the generator job doesn't.
service_flags=(-Dspring.aot.enabled=true)

cleanup() {
    docker rm -f "$trainer" "$db" >/dev/null 2>&1 || true
    docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT

log() { printf '\n==> %s\n' "$*"; }

log "Pass 1: building $train_image without an AOT cache"
./gradlew --quiet :pidoku-api:jibDockerBuild -Djib.to.image="$train_image"

log "Starting a throwaway Postgres for the training run"
docker network create "$network" >/dev/null
docker run -d --name "$db" --network "$network" \
    -e POSTGRES_DB=pidoku -e POSTGRES_PASSWORD=pidoku postgres:17-alpine >/dev/null
for _ in $(seq 60); do
    docker exec "$db" pg_isready -U postgres -d pidoku >/dev/null 2>&1 && break
    sleep 1
done
docker exec "$db" pg_isready -U postgres -d pidoku >/dev/null

db_env=(
    -e "DATABASE_URL=jdbc:postgresql://$db:5432/pidoku"
    -e DATABASE_USER=postgres
    -e DATABASE_PASSWORD=pidoku
)

# The image's own entrypoint (java plus the JVM flags and classpath from build.gradle), one word per
# line. Extra flags go right after "java", so training and production can't drift apart.
entrypoint_of() {
    docker image inspect "$1" --format '{{range .Config.Entrypoint}}{{println .}}{{end}}' | sed '/^$/d'
}

# Usage: run_entrypoint IMAGE [JVM FLAGS...]
# Runs the image until its Spring context is ready, then exits (spring.context.exit=onRefresh).
# Cloud Run-like limits: one CPU, as in production, and enough memory for the JVM that assembles
# the cache after the training JVM exits.
run_entrypoint() {
    local image=$1; shift
    local -a entrypoint
    mapfile -t entrypoint < <(entrypoint_of "$image")
    docker run --rm --network "$network" --cpus 1 --memory 2g "${db_env[@]}" \
        -v "$PWD/$aot_dir:/aot" --entrypoint "${entrypoint[0]}" "$image" \
        "$@" -Dspring.context.exit=onRefresh "${entrypoint[@]:1}"
}

# Usage: fastest_start LOG IMAGE [JVM FLAGS...]
# Starts the image three times, logging to LOG, and prints the fastest run in seconds. Spring exits
# (spring.context.exit=onRefresh) before it logs its own startup time, so this times the whole
# container run. Fails if any run fails.
fastest_start() {
    local log_file=$1; shift
    local best='' start elapsed
    for _ in 1 2 3; do
        start=${EPOCHREALTIME/./}
        run_entrypoint "$@" > "$log_file" 2>&1 || return 1
        elapsed=$(( ${EPOCHREALTIME/./} - start ))
        [[ -z $best || $elapsed -lt $best ]] && best=$elapsed
    done
    printf '%d.%d s' $(( best / 1000000 )) $(( best / 100000 % 10 ))
}

# Usage: exercise BASE_URL
# A round of the requests players make, repeated so the method profiles see them as hot. The
# signed-in endpoints get a malformed token: that runs the security filters and the 401 path
# without fetching Google's keys.
exercise() {
    local base=$1 today share_code
    today=$(date -u +%F)
    share_code=$(curl -fsS "$base/api/puzzles/daily/$today" | grep -o '"shareCode":"[^"]*"' | head -1 | cut -d'"' -f4)
    [[ -n $share_code ]] || { echo "The training database has no daily puzzle for $today" >&2; return 1; }
    local -a gets=(
        /actuator/health/liveness
        /actuator/health
        "/api/puzzles/daily/$today"
        "/api/puzzles/daily/$today/summary"
        "/api/puzzles/daily/$today/leaderboard?difficulty=easy"
        "/api/puzzles/daily/$today/leaderboard?difficulty=easy&clean=true"
        "/api/puzzles/$share_code"
        "/api/puzzles/random?difficulty=easy"
        /api/puzzles/NOSUCHCODE
    )
    local -a bad_token=(-H 'Authorization: Bearer not-a-real-token')
    for _ in $(seq 20); do
        for path in "${gets[@]}"; do
            curl -sS -o /dev/null "$base$path"
        done
        curl -sS -o /dev/null "${bad_token[@]}" "$base/api/players/me?today=$today"
        curl -sS -o /dev/null "${bad_token[@]}" -X POST -H 'Content-Type: application/json' \
            -d '{"puzzleId": 1}' "$base/api/attempts"
    done
}

rm -rf "$aot_dir"
mkdir -p "$aot_dir"
chmod 777 "$aot_dir"   # the image runs as an unprivileged user

# The service doesn't migrate (application-api.properties), so the schema comes first, then a few
# puzzles (one per difficulty, two days of dailies) for the training requests to read. Generation
# isn't what's being trained, so it gets every CPU.
log "Migrating the training database and generating a few puzzles"
mapfile -t job_entrypoint < <(entrypoint_of "$train_image")
docker run --rm --network "$network" "${db_env[@]}" -e SPRING_PROFILES_ACTIVE=generator \
    --entrypoint "${job_entrypoint[0]}" "$train_image" "${job_entrypoint[@]:1}" \
    --pidoku.generator.pool-size-per-difficulty=1 --pidoku.generator.daily-days-ahead=2 \
    > "$aot_dir/seed.log" 2>&1 || { cat "$aot_dir/seed.log"; exit 1; }

log "Baseline: starting without a cache"
baseline=$(fastest_start "$aot_dir/baseline.log" "$train_image" "${service_flags[@]}") \
    || { cat "$aot_dir/baseline.log"; exit 1; }

log "Training run: serving requests while recording the AOT cache"
docker run -d --name "$trainer" --network "$network" --cpus 1 --memory 2g "${db_env[@]}" \
    -p 127.0.0.1::8080 -v "$PWD/$aot_dir:/aot" --entrypoint "${job_entrypoint[0]}" "$train_image" \
    -XX:AOTCacheOutput=/aot/app.aot "${service_flags[@]}" "${job_entrypoint[@]:1}" >/dev/null
port=$(docker port "$trainer" 8080/tcp | head -1 | sed 's/.*://')
for _ in $(seq 120); do
    curl -fs "http://127.0.0.1:$port/actuator/health/liveness" >/dev/null 2>&1 && break
    [[ $(docker inspect -f '{{.State.Running}}' "$trainer") == true ]] || break
    sleep 1
done
exercise "http://127.0.0.1:$port" || { docker logs "$trainer"; exit 1; }
# SIGTERM: Spring shuts down, then the JVM records what it saw and starts a second JVM that
# assembles the cache, which takes a while; don't let docker kill it after the usual 10 s.
docker stop -t 600 "$trainer" >/dev/null
docker logs "$trainer" > "$aot_dir/training.log" 2>&1
[[ -s "$aot_dir/app.aot" ]] || { cat "$aot_dir/training.log"; echo "No AOT cache was written" >&2; exit 1; }

log "Pass 2: building $final_image with the AOT cache ($(du -h "$aot_dir/app.aot" | cut -f1))"
./gradlew --quiet :pidoku-api:jibDockerBuild -PwithAotCache=true -Djib.to.image="$final_image"

log "Checking that the final image uses the cache"
# AOTMode=on makes the JVM exit with an error instead of silently ignoring an unusable cache.
cached=$(fastest_start "$aot_dir/verify.log" "$final_image" -XX:AOTMode=on "${service_flags[@]}") \
    || { cat "$aot_dir/verify.log"; echo "The final image could not use its AOT cache" >&2; exit 1; }

log "Container start to Spring context ready (fastest of 3, one CPU): $baseline without the cache, $cached with it"

if [[ -n "$push_target" ]]; then
    log "Pushing $push_target"
    docker tag "$final_image" "$push_target"
    docker push "$push_target"
fi
