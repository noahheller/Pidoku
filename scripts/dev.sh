#!/usr/bin/env bash
# Runs Pidoku locally: the API against the Neon dev branch (from pidoku-api/.env) and the frontend
# dev server, which proxies /api to it. Ctrl+C stops both.
#
#   scripts/dev.sh              # API on :8080, frontend on http://localhost:5173
#   scripts/dev.sh --generate   # first fill the dev database with puzzles (pool and dailies), then run
#   scripts/dev.sh --help
#
# Needs JDK 25, Node 22+ and pidoku-api/.env set up as in the README ("Neon dev branch").
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_PORT=8080
WEB_PORT=5173

generate=false
for arg in "$@"; do
  case "$arg" in
    --generate) generate=true ;;
    -h | --help)
      sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (try --help)" >&2
      exit 2
      ;;
  esac
done

# --- Checks before starting anything ---
if [[ ! -f "$ROOT/pidoku-api/.env" ]]; then
  echo "pidoku-api/.env is missing. Create it with the Neon dev branch's connection details;" >&2
  echo "see \"Neon dev branch\" in README.md." >&2
  exit 1
fi
for port in "$API_PORT" "$WEB_PORT"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
    echo "Port $port is already in use. Is Pidoku (or something else) already running?" >&2
    exit 1
  fi
done
if [[ ! -d "$ROOT/pidoku-frontend/node_modules" || "$ROOT/pidoku-frontend/package-lock.json" -nt "$ROOT/pidoku-frontend/node_modules" ]]; then
  echo "Installing frontend dependencies..."
  npm --prefix "$ROOT/pidoku-frontend" install
fi

# --- Optional: fill the dev database with puzzles ---
if $generate; then
  echo "Generating puzzles into the dev database (Expert and Fiendish take a while)..."
  (cd "$ROOT" && ./gradlew :pidoku-api:bootRun --args='--spring.profiles.active=generator')
fi

# --- Start both, each in its own process group so stopping it also stops its children ---
pids=()
stop() {
  trap - INT TERM EXIT
  echo
  echo "Stopping..."
  for pid in "${pids[@]}"; do
    kill -TERM -- "-$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap stop INT TERM EXIT

# Each line of output is prefixed with [api] or [web] so the two logs can be told apart.
run() {
  local name="$1"
  shift
  setsid bash -c "$* 2>&1 | sed -u 's/^/[$name] /'" &
  pids+=("$!")
}

run api "cd '$ROOT' && exec ./gradlew :pidoku-api:bootRun"

echo "Waiting for the API on :$API_PORT..."
for _ in $(seq 1 120); do
  if curl -fs "http://localhost:$API_PORT/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "${pids[0]}" 2>/dev/null; then
    echo "The API stopped while starting; see the [api] lines above." >&2
    exit 1
  fi
  sleep 1
done
if ! curl -fs "http://localhost:$API_PORT/actuator/health" >/dev/null 2>&1; then
  echo "The API didn't become healthy within 2 minutes." >&2
  exit 1
fi

run web "cd '$ROOT/pidoku-frontend' && exec npx vite --port $WEB_PORT --strictPort"

echo
echo "Pidoku is running: http://localhost:$WEB_PORT  (API: http://localhost:$API_PORT). Ctrl+C to stop."
echo

# Stop everything as soon as either one exits.
wait -n
