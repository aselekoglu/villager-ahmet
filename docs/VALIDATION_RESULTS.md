# Validation results — 2026-10-12

## Verified

- `./scripts/doctor.sh` identifies Java 21.0.12.1, Maven 3.10.0 and Node.js 22.19.0 when Homebrew Java/Maven are on `PATH`. Paper is pinned to 1.21.6 build 48; Citizens is pinned to 2.0.41 build 4138 with the 1.21_R5 adapter.
- `mvn -B -ntp -Dmaven.repo.local=/private/tmp/villager-ahmet-m2 clean package` succeeded on Java 21: **16 tests, 0 failures**. This includes bounded task/cancellation, journal recovery, chest planning, navigation retry and safe adjacent summon-target tests.
- `npm test` passed: **8 tests, 0 failures**. Tests cover direct TypeSafe JEV request shape, direct OpenAI endpoint/model contract, bounded offered-action validation, Turkish chat tasks and labelled fallback behavior.
- `./scripts/smoke.sh` passed: addressed Turkish chat, bounded mock decision, memory event persistence and missing-token rejection. It ran in mock mode; it did not call a live model.
- `./scripts/setup.sh` completed and copied the tested plugin JAR to `server/plugins/VillagerAhmet.jar`. The source and installed JAR SHA-256 values matched.
- Created `dist/villager-ahmet-plugins-paper-1.21.6.zip` for transfer to the existing server. It contains the tested Ahmet JAR, pinned Citizens JAR, Turkish installation notes and SHA-256 checksums; archive contents and embedded JAR hashes were verified. The ZIP itself is ignored by Git.
- A fresh project-owned Paper server booted with Java 21, Paper `1.21.6-48-main@4d854e6`, Citizens `2.0.41` build `4138`, and VillagerAhmet `0.1.0`. Startup logged `Villager Ahmet ready` and `Done (39.399s)` with no plugin exception. It created a new world under `server/`; no existing user world was present in that test directory.
- The user accepted the Minecraft EULA. `server/eula.txt` now records `eula=true` for that fresh project server. `online-mode=true` and `white-list=true` remain enabled.
- The local brain health check returned HTTP 200 on `127.0.0.1:3197`; it is not bound to a public interface.
- The authorized TypeSafe key was copied to ignored `brain/.env` with mode `0600`; its value was never printed. `GET /v1/models` returned `jev-latest` and `jev-preview`. A live `/v1/systemone` request using `jev-latest` returned actual model `jev-1.13.0` and selected the offered `walk_tree` action. This is a real JEV call, separate from fallback/mock checks.
- `javap` against the pinned Citizens JAR confirmed `NPC.teleport(Location, PlayerTeleportEvent.TeleportCause)` exists. The plugin keeps normal Citizens navigation non-teleporting. The new operator-only `/ahmet summon` teleports an idle NPC to a loaded, passable block beside the operator and is rejected during active work or journal recovery.

## Not yet verified

- Two-client multiplayer permissions, `/ahmet spawn`, chest persistence across restart, Citizens pathing, oak harvesting/deposit, cancellation, restart recovery and chest-full behavior still need in-game checks. One client summon, chest binding and a failed collection attempt are recorded below.
- The user's current server at `10.0.0.11:62083` answered a read-only Minecraft status handshake: version `1.21.6`, 2/8 players. Earlier ports `59519` and `61759` did not yield a status response. No login or command was sent. The server's plugin directory/console route is still unavailable, so Ahmet has **not** been installed or teleported there. The local test server at port `25565` was a separate fresh world and has been stopped cleanly.
- The user confirmed the TLauncher `ahmet` profile is offline. No client login was attempted because the project forbids bypassing Minecraft account authentication. The official Launcher account is already present in the server status sample, so launching it again could disconnect the user.
- `OPENAI_API_KEY` is still unset. The chat route is implemented and mocked tests pass for direct `https://api.openai.com/v1/chat/completions` with `gpt-6-luna`, but a real OpenAI conversation was not sent. The secure OpenAI key picker/local destination flow has not completed.
- The hosted GitHub Actions workflow has not run because no Git remote is configured. A local repository is used for this handoff; equivalent Node, local HTTP smoke and Maven checks were run locally.
- The journal's crash-recovery behavior has fixture coverage but has not been crash-tested against a running Minecraft world. Tree identification remains a private-world oak/leaves heuristic, not a region-protection system or native villager mining.

## Imported-save local server — 2026-10-12

- The user's save at `/Users/aselekoglu/Downloads/Ajanlar ve Ananlar/Ajanlar ve Ananlar` was copied to `server-live/world`. A SHA-256 manifest verified all **45 files / 30,188,019 bytes** matched; only `.DS_Store` and the transient `session.lock` were excluded from the copy. The source folder was left untouched.
- The copy's `level.dat` DataVersion is 4435, matching Minecraft 1.21.6. Paper 1.21.6 build 48 booted this copied world with Citizens 2.0.41 build 4138 and VillagerAhmet 0.1.0. Paper's Nether and End folder migrations completed in `server-live/`; those changes did not touch the source save.
- The local server remains running on TCP port 25565 with `online-mode=true` and whitelist enabled. Official Java account `aselekoglu` was added to whitelist and operators (`ops.json`, level 4). Subsequent client and summon evidence is recorded below.
- The brain started on `127.0.0.1:3197`. Live OpenAI chat is unavailable because `OPENAI_API_KEY` is not configured; the real JEV result recorded above is separate from this world boot.
- The user then joined the imported world with Minecraft 1.21.6 as `aselekoglu`. Server logs show successful joins and `/ahmet summon`; the supplied screenshot shows the named villager NPC beside the player and Ahmet's “yanına geldim” response. This is observed in-game summon evidence for the Citizens villager implementation.
- In the same session, `/ahmet chest` saved the delivery chest at `world (3,73,143)`. Chat started an 8-log task; the persisted task state showed `requested=8`, `collected=0`, `cargo=0`. The screenshot and server state show that tree selection/harvesting did **not** succeed.
- Diagnosis: the current MVP resolves generic “odun” requests to **oak logs only**. `findTree` scans loaded chunks within a 16-block horizontal radius and only ±2 vertical blocks from Ahmet; `isTree` additionally requires nearby oak leaves. “Git bul” does not expand that bounded search or authorize a new movement action. The pale trunks visible through the windows look like birch, but that species identification is an image-based inference, not a block-state inspection. The no-tree warning repeats on bounded retries, so it can appear as chat spam.

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

The user's copied save is already prepared and running separately. To launch it after a clean shutdown (or on the same Mac after the current process stops):

```bash
cd /Users/aselekoglu/villager-ahmet
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/maven/bin:$PATH"
AHMET_SERVER_DIR=server-live ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
```

Connect the official `aselekoglu` Java 1.21.6 client to `localhost:25565`, then run `/ahmet summon` in-game. The EULA was accepted by the user; the environment flag is only needed because this script also supports fresh server folders.

## Provider references

- TypeSafe API schema: <https://api.typesafe.ai/openapi.json>
- OpenAI GPT-6 Luna model page: <https://developers.openai.com/api/docs/models/gpt-6-luna>
- OpenAI Chat Completions reference: <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create>
