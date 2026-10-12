#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/brain"
TOKEN="$(python3 -c 'import secrets;print(secrets.token_hex(16))')"
PORT=3198
SMOKE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ahmet-smoke.XXXXXX")"
LOG_PATH="$SMOKE_DIR/server.log"
MEMORY_PATH="$SMOKE_DIR/memory.json"
BRIDGE_TOKEN="$TOKEN" BRAIN_PORT="$PORT" AHMET_CHAT_MODE=mock AHMET_DECISION_MODE=mock MEMORY_PATH="$MEMORY_PATH" node src/server.mjs >"$LOG_PATH" 2>&1 & PID=$!
cleanup(){ kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; rm -rf "$SMOKE_DIR"; }
trap cleanup EXIT INT TERM
for i in {1..30}; do curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 && break; sleep .2; done
BODY='{"actorUuid":"test-player","actorName":"Ata","text":"Ahmet, bulabildiğin 20 tane odun topla","context":{"busy":false,"chestBound":true}}'
REPLY="$(curl -fsS "http://127.0.0.1:$PORT/api/chat" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$BODY")"
python3 -c 'import sys,json;d=json.load(sys.stdin);assert d["task"]=={"type":"collect_wood","material":"any","amount":20};assert d["taskSource"]=="heuristic";print("Chat contract OK:",d["reply"])' <<< "$REPLY"
DECISION="$(curl -fsS "http://127.0.0.1:$PORT/api/decide" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"observation":{"task":"collect_wood"},"actions":[{"id":"target_a","description":"Select birch log"},{"id":"target_b","description":"Select oak log"},{"id":"wait","description":"Wait"}]}')"
python3 -c 'import sys,json;d=json.load(sys.stdin);assert d["id"]=="target_a";print("Decision contract OK:",d)' <<< "$DECISION"
EVENT="$(curl -fsS "http://127.0.0.1:$PORT/api/event" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"type":"task_delivered","actor":"test-player","count":20}')"
python3 -c 'import sys,json;assert json.load(sys.stdin)["ok"] is True;print("Memory event contract OK")' <<< "$EVENT"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/decide" -H 'Content-Type: application/json' -d '{}')"
[[ "$STATUS" == 401 ]] && echo 'Token auth OK' || { echo "Authorization check failed: $STATUS"; exit 1; }
echo 'All brain HTTP smoke tests passed (mock, no live Minecraft or JEV).'
