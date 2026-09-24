#!/usr/bin/env bash
# Starts all four Meridian services locally. Ctrl+C stops them all.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.logs"
mkdir -p "$LOGS"

pids=()
cleanup() {
  echo "Stopping services..."
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT INT TERM

start_python() {
  local name="$1" port="$2"
  (
    cd "$ROOT/services/$name"
    python3 -m pip install -q -r requirements.txt
    exec python3 -m uvicorn app.main:app --port "$port"
  ) >"$LOGS/$name.log" 2>&1 &
  pids+=($!)
  echo "  $name        → http://localhost:$port  (logs: .logs/$name.log)"
}

echo "Starting Meridian Platform..."
start_python audit-log 8003
start_python pii-vault 8002

(cd "$ROOT/services/transaction-service" && exec mvn -q spring-boot:run) \
  >"$LOGS/transaction-service.log" 2>&1 &
pids+=($!)
echo "  transaction-service → http://localhost:8081  (logs: .logs/transaction-service.log)"

(cd "$ROOT/services/auth-gateway" && npm install --silent && exec npm start --silent) \
  >"$LOGS/auth-gateway.log" 2>&1 &
pids+=($!)
echo "  auth-gateway + UI   → http://localhost:3000  (logs: .logs/auth-gateway.log)"

echo
echo "Open http://localhost:3000 once all services are up (Spring Boot takes ~20s)."
wait
