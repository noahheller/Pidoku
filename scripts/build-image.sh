#!/usr/bin/env bash
# Builds the pidoku-api container image with a JVM AOT cache (JDK 25, JEP 483/514/515).
#
# Usage:
#   scripts/build-image.sh            builds pidoku-api:latest into the local Docker daemon
#   scripts/build-image.sh IMAGE      also pushes it as IMAGE, e.g.
#                                     us-east4-docker.pkg.dev/<project>/<repo>/pidoku-api:<git-sha>
#
# The AOT cache holds the classes the JVM loaded and linked during a training run, so a cold
# start skips most of that work. A cache only works with the exact JDK build, JVM flags and
# classpath that created it, so the training run has to happen inside the image itself, which
# Jib can't do (it assembles layers and never runs the app). Hence two passes:
#
#   1. Jib builds the image without a cache.
#   2. That image is started once against a throwaway Postgres, with Spring exiting right after
#      startup (spring.context.exit=onRefresh), and the JVM writes the cache on exit.
#   3. Jib builds the image again with the cache added. The layers are reproducible, so the
#      classpath JARs are byte-identical (same paths, sizes and timestamps) to the training run.
#   4. The final image is started once with -XX:AOTMode=on, which fails if the cache can't be used.
#
# Requires Docker. Pushing uses Docker's credentials (gcloud auth configure-docker <region>-docker.pkg.dev).
set -euo pipefail

cd "$(dirname "$0")/.."

push_target=${1:-}
train_image=pidoku-api:aot-training
final_image=pidoku-api:latest
aot_dir=pidoku-api/build/aot
network=pidoku-aot-$$
db=pidoku-aot-db-$$

cleanup() {
    docker rm -f "$db" >/dev/null 2>&1 || true
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

# Run the image's own entrypoint (java plus the JVM flags and classpath from build.gradle), with
# extra flags inserted after "java", so training and production can't drift apart.
# Cloud Run-like limits: one CPU, as in production, and enough memory for the JVM that assembles
# the cache after the training JVM exits.
run_entrypoint() {
    local image=$1; shift
    local -a entrypoint
    mapfile -t entrypoint < <(docker image inspect "$image" --format '{{range .Config.Entrypoint}}{{println .}}{{end}}' | sed '/^$/d')
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

rm -rf "$aot_dir"
mkdir -p "$aot_dir"
chmod 777 "$aot_dir"   # the image runs as an unprivileged user

log "Baseline: starting without a cache"
baseline=$(fastest_start "$aot_dir/baseline.log" "$train_image") \
    || { cat "$aot_dir/baseline.log"; exit 1; }

log "Training run: recording the AOT cache"
run_entrypoint "$train_image" -XX:AOTCacheOutput=/aot/app.aot > "$aot_dir/training.log" 2>&1 \
    || { cat "$aot_dir/training.log"; exit 1; }
[[ -s "$aot_dir/app.aot" ]] || { cat "$aot_dir/training.log"; echo "No AOT cache was written" >&2; exit 1; }

log "Pass 2: building $final_image with the AOT cache ($(du -h "$aot_dir/app.aot" | cut -f1))"
./gradlew --quiet :pidoku-api:jibDockerBuild -PwithAotCache=true -Djib.to.image="$final_image"

log "Checking that the final image uses the cache"
# AOTMode=on makes the JVM exit with an error instead of silently ignoring an unusable cache.
cached=$(fastest_start "$aot_dir/verify.log" "$final_image" -XX:AOTMode=on) \
    || { cat "$aot_dir/verify.log"; echo "The final image could not use its AOT cache" >&2; exit 1; }

log "Container start to Spring context ready (fastest of 3, one CPU): $baseline without the cache, $cached with it"

if [[ -n "$push_target" ]]; then
    log "Pushing $push_target"
    docker tag "$final_image" "$push_target"
    docker push "$push_target"
fi
