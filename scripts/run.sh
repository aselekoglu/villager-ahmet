#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[[ -f "$ROOT/server/paper.jar" && -f "$ROOT/server/plugins/Citizens-2.0.41-b4138.jar" && -f "$ROOT/server/plugins/VillagerAhmet.jar" ]] || { echo 'Run ./scripts/setup.sh first';exit 1; }
JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
[[ "${JAVA_VERSION%%.*}" == 21 ]] || { echo "Java 21 is required; found ${JAVA_VERSION:-unknown}.";exit 1; }
if [[ ! -f "$ROOT/server/eula.txt" ]] || ! grep -q '^eula=true$' "$ROOT/server/eula.txt"; then
  if [[ "${ACCEPT_MINECRAFT_EULA:-}" != true ]]; then
    echo 'Read the Minecraft EULA at https://aka.ms/MinecraftEULA'
    echo 'Only if you agree: ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh'
    exit 1
  fi
  echo 'eula=true' > "$ROOT/server/eula.txt"
fi
cd "$ROOT/brain"
node src/server.mjs & BRAIN_PID=$!
trap 'kill "$BRAIN_PID" 2>/dev/null || true' EXIT INT TERM
cd "$ROOT/server"
java -Xms1G -Xmx3G -jar paper.jar --nogui
