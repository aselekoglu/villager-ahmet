#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="${AHMET_SERVER_DIR:-$ROOT/server}"
[[ "$SERVER" == /* ]] || SERVER="$ROOT/$SERVER"
[[ -f "$SERVER/paper.jar" && -f "$SERVER/plugins/Citizens-2.0.41-b4138.jar" && -f "$SERVER/plugins/VillagerAhmet.jar" ]] || { echo 'Run ./scripts/setup.sh first for the selected AHMET_SERVER_DIR';exit 1; }
JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
[[ "${JAVA_VERSION%%.*}" == 21 ]] || { echo "Java 21 is required; found ${JAVA_VERSION:-unknown}.";exit 1; }
if [[ ! -f "$SERVER/eula.txt" ]] || ! grep -q '^eula=true$' "$SERVER/eula.txt"; then
  if [[ "${ACCEPT_MINECRAFT_EULA:-}" != true ]]; then
    echo 'Read the Minecraft EULA at https://aka.ms/MinecraftEULA'
    echo 'Only if you agree: ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh'
    exit 1
  fi
  echo 'eula=true' > "$SERVER/eula.txt"
fi
cd "$ROOT/brain"
node src/server.mjs & BRAIN_PID=$!
trap 'kill "$BRAIN_PID" 2>/dev/null || true' EXIT INT TERM
cd "$SERVER"
java -Xms1G -Xmx3G -jar paper.jar --nogui
