# Validation results — 2026-10-12

## Verified

- `./scripts/doctor.sh` identifies Java 21.0.12.1, Maven 3.10.0 and Node.js 22.19.0 when Homebrew Java/Maven are on `PATH`. Paper is pinned to 1.21.6 build 48; Citizens is pinned to 2.0.41 build 4138 with the 1.21_R5 adapter.
- `mvn -B -ntp -Dmaven.repo.local=/private/tmp/villager-ahmet-m2 clean package` succeeded on Java 21: **16 tests, 0 failures**. This includes bounded task/cancellation, journal recovery, chest planning, navigation retry and safe adjacent summon-target tests.
- `npm test` passed: **8 tests, 0 failures**. Tests cover direct TypeSafe JEV request shape, direct OpenAI endpoint/model contract, bounded offered-action validation, Turkish chat tasks and labelled fallback behavior.
- `./scripts/smoke.sh` passed: addressed Turkish chat, bounded mock decision, memory event persistence and missing-token rejection. It ran in mock mode; it did not call a live model.
- `./scripts/setup.sh` completed and copied the tested plugin JAR to `server/plugins/VillagerAhmet.jar`. The source and installed JAR SHA-256 values matched.
- A fresh project-owned Paper server booted with Java 21, Paper `1.21.6-48-main@4d854e6`, Citizens `2.0.41` build `4138`, and VillagerAhmet `0.1.0`. Startup logged `Villager Ahmet ready` and `Done (39.399s)` with no plugin exception. It created a new world under `server/`; no existing user world was present in that test directory.
- The user accepted the Minecraft EULA. `server/eula.txt` now records `eula=true` for that fresh project server. `online-mode=true` and `white-list=true` remain enabled.
- The local brain health check returned HTTP 200 on `127.0.0.1:3197`; it is not bound to a public interface.
- The authorized TypeSafe key was copied to ignored `brain/.env` with mode `0600`; its value was never printed. `GET /v1/models` returned `jev-latest` and `jev-preview`. A live `/v1/systemone` request using `jev-latest` returned actual model `jev-1.13.0` and selected the offered `walk_tree` action. This is a real JEV call, separate from fallback/mock checks.
- `javap` against the pinned Citizens JAR confirmed `NPC.teleport(Location, PlayerTeleportEvent.TeleportCause)` exists. The plugin keeps normal Citizens navigation non-teleporting. The new operator-only `/ahmet summon` teleports an idle NPC to a loaded, passable block beside the operator and is rejected during active work or journal recovery.

## Not yet verified

- No Minecraft client has joined the fresh project server. Real Villager rendering, `/ahmet spawn`, `/ahmet summon`, player chat, Citizens movement, log harvesting, chest deposit, cancellation, restart recovery and two-player permissions still need in-game checks.
- The user's current server at `10.0.0.11:62083` answered a read-only Minecraft status handshake: version `1.21.6`, 2/8 players. Earlier ports `59519` and `61759` did not yield a status response. No login or command was sent. The server's plugin directory/console route is still unavailable, so Ahmet has **not** been installed or teleported there. The local test server at port `25565` was a separate fresh world and has been stopped cleanly.
- The user confirmed the TLauncher `ahmet` profile is offline. No client login was attempted because the project forbids bypassing Minecraft account authentication. The official Launcher account is already present in the server status sample, so launching it again could disconnect the user.
- `OPENAI_API_KEY` is still unset. The chat route is implemented and mocked tests pass for direct `https://api.openai.com/v1/chat/completions` with `gpt-6-luna`, but a real OpenAI conversation was not sent. The secure OpenAI key picker/local destination flow has not completed.
- The hosted GitHub Actions workflow has not run because no Git remote is configured. A local repository is used for this handoff; equivalent Node, local HTTP smoke and Maven checks were run locally.
- The journal's crash-recovery behavior has fixture coverage but has not been crash-tested against a running Minecraft world. Tree identification remains a private-world oak/leaves heuristic, not a region-protection system or native villager mining.

## Local test-server commands

On this Mac, with Homebrew Java 21 and Maven installed:

```bash
cd /Users/aselekoglu/villager-ahmet
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/maven/bin:$PATH"
./scripts/doctor.sh
AHMET_MAVEN_REPO=/private/tmp/villager-ahmet-m2 ./scripts/setup.sh
ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
```

The Maven repository override is useful in this restricted environment; omit it on a normal machine if `~/.m2` is writable. The run command starts both the brain and Paper server. The fresh server has a whitelist enabled; add and op the correct Java username before connecting. As operator, use `/ahmet spawn` for a new villager or `/ahmet summon` for an existing idle Ahmet.

## Provider references

- TypeSafe API schema: <https://api.typesafe.ai/openapi.json>
- OpenAI GPT-6 Luna model page: <https://developers.openai.com/api/docs/models/gpt-6-luna>
- OpenAI Chat Completions reference: <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create>
