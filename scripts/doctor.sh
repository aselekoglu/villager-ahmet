#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="${AHMET_SERVER_DIR:-$ROOT/server}"
[[ "$SERVER" == /* ]] || SERVER="$ROOT/$SERVER"
echo 'Villager Ahmet environment diagnostics'
for bin in java javac mvn python3 node curl; do if command -v "$bin" >/dev/null;then printf '[OK] %-10s %s\n' "$bin" "$(command -v "$bin")";else echo "[MISSING] $bin";fi;done
if command -v java >/dev/null; then
  JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
  if [[ "${JAVA_VERSION%%.*}" == 21 ]]; then echo "[OK] Java $JAVA_VERSION";else echo "[TODO] Java 21 required (found ${JAVA_VERSION:-unknown})";fi
fi
if command -v mvn >/dev/null; then
  MAVEN_VERSION="$(mvn -version 2>&1 | awk '$1 == "Apache" && $2 == "Maven" {print $3; exit}')"
  if [[ "$MAVEN_VERSION" =~ ^([0-9]+)\.([0-9]+) ]] && (( BASH_REMATCH[1] > 3 || (BASH_REMATCH[1] == 3 && BASH_REMATCH[2] >= 9) ));then echo "[OK] Maven $MAVEN_VERSION";else echo "[TODO] Maven 3.9+ required (found ${MAVEN_VERSION:-unknown})";fi
fi
if command -v node >/dev/null; then
  NODE_VERSION="$(node --version | sed 's/^v//')"
  if [[ "${NODE_VERSION%%.*}" =~ ^[0-9]+$ ]] && (( ${NODE_VERSION%%.*} >= 20 ));then echo "[OK] Node.js $NODE_VERSION";else echo "[TODO] Node.js 20+ required (found $NODE_VERSION)";fi
fi
[[ -f "$ROOT/brain/.env" ]] && echo '[OK] brain/.env exists' || echo '[TODO] brain/.env missing'
if [[ -f "$ROOT/brain/.env" ]]; then
  if grep -q '^TYPESAFE_API_KEY=.' "$ROOT/brain/.env";then echo '[OK] TypeSafe JEV key configured';else echo '[TODO] TYPESAFE_API_KEY missing; live JEV decisions unavailable';fi
  if grep -q '^OPENAI_API_KEY=.' "$ROOT/brain/.env";then echo '[OK] OpenAI chat key configured';else echo '[TODO] OPENAI_API_KEY missing; live GPT-6 Luna chat unavailable';fi
fi
[[ -f "$SERVER/plugins/VillagerAhmet.jar" ]] && echo '[OK] Paper plugin built' || echo '[TODO] Paper plugin not built'
[[ -f "$SERVER/plugins/Citizens-2.0.41-b4138.jar" ]] && echo '[OK] Citizens 2.0.41 build 4138 found' || echo '[TODO] Citizens 2.0.41 build 4138 not found'
[[ -f "$SERVER/paper.jar" ]] && echo '[OK] Paper server found' || echo '[TODO] Paper server not found'
if [[ -f "$SERVER/paper.version" ]]; then
  PAPER_VERSION="$(cat "$SERVER/paper.version")"
  if [[ "$PAPER_VERSION" == *"1.21.6"* ]];then echo "[OK] Paper $PAPER_VERSION";else echo "[TODO] Paper 1.21.6 required (found $PAPER_VERSION)";fi
else
  echo '[TODO] Paper version marker missing; rerun ./scripts/setup.sh to verify it'
fi
if [[ -f "$SERVER/server.properties" ]]; then
  grep -q '^online-mode=true$' "$SERVER/server.properties" && echo '[OK] online-mode=true' || echo '[TODO] online-mode should remain true for legitimate accounts'
  grep -q '^white-list=true$' "$SERVER/server.properties" && echo '[OK] whitelist enabled' || echo '[TODO] enable white-list before multiplayer launch'
fi
[[ -f "$SERVER/eula.txt" && "$(grep -c '^eula=true$' "$SERVER/eula.txt" || true)" -gt 0 ]] && echo '[OK] EULA accepted by user' || echo '[TODO] EULA acceptance required before launch'
