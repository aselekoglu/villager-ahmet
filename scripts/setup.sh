#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="$ROOT/server"
mkdir -p "$SERVER/plugins/VillagerAhmet"
for cmd in java python3 mvn node; do command -v "$cmd" >/dev/null || { echo "Missing $cmd. Install Java 21, Node.js 20+, Python 3 and Maven 3.9+, then rerun."; exit 1; }; done
JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
[[ "${JAVA_VERSION%%.*}" == 21 ]] || { echo "Java 21 is required; found ${JAVA_VERSION:-unknown}."; exit 1; }
MAVEN_VERSION="$(mvn -version 2>&1 | awk '$1 == "Apache" && $2 == "Maven" {print $3; exit}')"
[[ "$MAVEN_VERSION" =~ ^([0-9]+)\.([0-9]+) ]] || { echo 'Could not determine Maven version.'; exit 1; }
(( BASH_REMATCH[1] > 3 || (BASH_REMATCH[1] == 3 && BASH_REMATCH[2] >= 9) )) || { echo "Maven 3.9+ is required; found $MAVEN_VERSION."; exit 1; }
NODE_VERSION="$(node --version | sed 's/^v//')"
[[ "${NODE_VERSION%%.*}" =~ ^[0-9]+$ ]] && (( ${NODE_VERSION%%.*} >= 20 )) || { echo "Node.js 20+ is required; found $NODE_VERSION."; exit 1; }
if [[ ! -f "$SERVER/paper.jar" ]]; then
  echo "Downloading a stable Paper 1.21.6 server JAR from PaperMC..."
  python3 - "$SERVER/paper.jar" <<'PYCODE'
import json,sys,urllib.request
ua='villager-ahmet/0.1 (+https://github.com/aselekoglu)'
req=urllib.request.Request('https://fill.papermc.io/v3/projects/paper/versions/1.21.6/builds',headers={'User-Agent':ua})
with urllib.request.urlopen(req,timeout=25) as r: builds=json.load(r)
build=next((b for b in builds if b.get('channel')=='STABLE'),None)
if build is None: raise SystemExit('No stable Paper build for 1.21.6')
download=build['downloads']['server:default']
url=download['url'];sha=download['checksums']['sha256']
req=urllib.request.Request(url,headers={'User-Agent':ua})
with urllib.request.urlopen(req,timeout=90) as r: content=r.read()
import hashlib
if hashlib.sha256(content).hexdigest()!=sha:raise SystemExit('Paper checksum mismatch')
with open(sys.argv[1],'wb') as f:f.write(content)
print('Downloaded verified Paper build',build['id'])
PYCODE
fi
PAPER_VERSION_OUTPUT="$(java -jar "$SERVER/paper.jar" --version 2>&1)" || { echo 'Could not verify the Paper JAR version; check network access and preserve server/.'; exit 1; }
[[ "$PAPER_VERSION_OUTPUT" == *"1.21.6"* ]] || { echo "Expected Paper 1.21.6; found: $(printf '%s\n' "$PAPER_VERSION_OUTPUT" | tail -1)"; exit 1; }
printf '%s\n' "$PAPER_VERSION_OUTPUT" | tail -1 > "$SERVER/paper.version"
if find "$SERVER/plugins" -maxdepth 1 -type f -name 'Citizens*.jar' ! -name 'Citizens-2.0.41-b4138.jar' -print -quit | grep -q .; then
  echo 'A different Citizens JAR is already present. Preserve it and use a clean test server/plugins directory.'
  exit 1
fi
if [[ ! -f "$SERVER/plugins/Citizens-2.0.41-b4138.jar" ]]; then
  echo "Downloading pinned Citizens 2.0.41 build 4138 from the official public Jenkins..."
  python3 - "$SERVER/plugins/Citizens-2.0.41-b4138.jar" <<'PYCODE'
import sys,urllib.request
url='https://ci.citizensnpcs.co/job/Citizens2/4138/artifact/dist/target/Citizens-2.0.41-b4138.jar'
req=urllib.request.Request(url,headers={'User-Agent':'VillagerAhmet/0.1 (+https://github.com/aselekoglu)'})
with urllib.request.urlopen(req,timeout=90) as r:content=r.read()
if not content.startswith(b'PK'):raise SystemExit('Invalid Citizens JAR download')
with open(sys.argv[1],'wb') as f:f.write(content)
PYCODE
fi
python3 - "$SERVER/plugins/Citizens-2.0.41-b4138.jar" <<'PYCODE'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as jar:
    names=jar.namelist()
    plugin=jar.read('plugin.yml').decode('utf-8','replace')
    if not any(line.strip()=='name: Citizens' for line in plugin.splitlines()):
        raise SystemExit('Citizens plugin.yml not found in pinned JAR')
    if not any('net/citizensnpcs/nms/v1_21_R5/' in name for name in names):
        raise SystemExit('Pinned Citizens JAR lacks the Minecraft 1.21_R5 adapter')
print('Verified Citizens 2.0.41 build 4138 and its 1.21_R5 adapter.')
PYCODE
if [[ ! -f "$ROOT/brain/.env" ]]; then
  token="$(python3 -c 'import secrets;print(secrets.token_hex(24))')"
  sed "s/CHANGE_ME_TO_A_RANDOM_SECRET/$token/" "$ROOT/brain/.env.example" > "$ROOT/brain/.env"
  chmod 600 "$ROOT/brain/.env"
fi
TOKEN="$(sed -n 's/^BRIDGE_TOKEN=//p' "$ROOT/brain/.env" | head -1)"
[[ -n "$TOKEN" && "$TOKEN" != "CHANGE_ME_TO_A_RANDOM_SECRET" ]] || { echo 'Set a secure BRIDGE_TOKEN in brain/.env'; exit 1; }
# Only seed configuration initially; do not destroy a user's live config.
if [[ ! -f "$SERVER/plugins/VillagerAhmet/config.yml" ]]; then
  sed "s/CHANGE_ME_TO_A_RANDOM_SECRET/$TOKEN/" "$ROOT/paper-plugin/src/main/resources/config.yml" > "$SERVER/plugins/VillagerAhmet/config.yml"
fi
MAVEN_ARGS=(-B -ntp)
if [[ -n "${AHMET_MAVEN_REPO:-}" ]]; then
  MAVEN_ARGS+=("-Dmaven.repo.local=$AHMET_MAVEN_REPO")
fi
( cd "$ROOT/paper-plugin" && mvn "${MAVEN_ARGS[@]}" package -DskipTests )
cp "$ROOT/paper-plugin/target/villager-ahmet-0.1.0-SNAPSHOT.jar" "$SERVER/plugins/VillagerAhmet.jar"
if [[ ! -f "$SERVER/server.properties" ]]; then
  cat > "$SERVER/server.properties" <<'PROPS'
online-mode=true
white-list=true
server-port=25565
motd=Villager Ahmet MVP
max-players=6
spawn-protection=0
PROPS
fi
echo 'Setup complete. Set TYPESAFE_API_KEY and OPENAI_API_KEY in brain/.env for separate live JEV and chat calls.'
echo 'Use: ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh (only after reading and accepting the EULA).'
echo 'Whitelist your Minecraft Java accounts from the server console: whitelist add <username>'
