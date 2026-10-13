# Codex Handoff — Villager Ahmet

## 1. Project Overview & Architecture
Villager Ahmet is an autonomous, AI-driven assistant in Minecraft Java Edition running on **Paper 1.21.6 (build 48)** with **Citizens 2.0.41 (build 4138)** and **Java 21**, coordinated by a local **Node.js (v22)** brain daemon (`127.0.0.1:3197`).

### Architectural Division of Responsibility
- **Java Paper Plugin (`paper-plugin/`)**: Authoritative owner of world mutations, entity pathfinding, item transactions, task state, write-ahead journaling, and safe boundaries. All Bukkit/Citizens calls stay on the server main thread.
- **Node Brain Daemon (`brain/`)**: Language understanding, structured task extraction, conversational memory, and decision candidate selection. Strictly distinguishes **TypeSafe JEV** (`POST https://api.typesafe.ai/v1/systemone` using model `jev-latest`) for bounded decision selection from **OpenAI GPT-6 Luna** (`POST https://api.openai.com/v1/chat/completions`) for conversational chat.
- **Private LAN Network Policy**: Operating on private LAN (`10.0.0.140:25565`) with `online-mode=false` (to accommodate operator's offline TLauncher client profile) and whitelist enabled. Strictly private; never port-forwarded or exposed publicly.

---

## 2. Implemented Capabilities & Verified Features

### A. Real Citizens VILLAGER NPC & Bounded Movement
- Instantiates a genuine `EntityType.VILLAGER` Citizens NPC (never a player bot in villager disguise).
- Bounded navigation using Citizens pathfinding with watchdog stall detection (`NavigationWatchdog`) and exponential backoff retry (`NavigationRetry`).
- **Teleportation Invariant**: Normal task movement never teleports. Only an explicit operator `/ahmet summon` can reposition Ahmet while idle to a safe, loaded adjacent location.

### B. Natural Resource Harvesting & Full Item Catalog
- Complete 1.21.6 item catalog with 1,414 items (`MaterialCatalog.java`).
- Supports general wood (`odun`) across all 12 wood species, plus common resources (cobblestone, coal, raw iron, raw gold, dirt, sand).
- Direct bounded operator/player commands: `/ahmet collect <item|odun> <1-64>`.

### C. Double-Chest & Single-Chest Delivery
- Fixed double-chest inspection using `Chest.getInventory()` (Paper DoubleInventory view providing full 54 slots, avoiding the 27-slot single-half limitation of `getBlockInventory()`).
- Atomic capacity planner (`CargoDepositPlanner`) fills existing partial stacks and empty slots up to stack limits.
- Live in-game verified: Ahmet autonomously walked to double chest at `world (3, 73, 144)` and deposited carried birch logs cleanly into slot 27 of the empty second half.

### D. Write-Ahead Operations Journal & Crash Recovery
- Durable write-ahead journal (`OperationJournal` -> `operations.json`) logs every harvest and deposit before mutating world/chest state.
- **Battle-tested during host disk full event**: Recovered from 100% APFS disk exhaustion without data loss; `recoveryBlocked` engaged automatically, and state reconciled cleanly on restart with 0 items lost.

### E. Player Structure & Home Protection
- `PlayerPlacedWoodRegistry`: Tracks player-placed logs/stems via `BlockPlaceEvent` in `config.yml`, preventing accidental harvesting of player builds.
- `ProtectedHomeArea` & `/ahmet protecthome <0-32>`: Configurable spherical exclusion zone around player home (default 10 blocks) protecting pre-existing houses. Natural leaf-neighbor heuristics redirect harvesting to wild trees.

### F. Resource Exploration & Wandering (Latest Addition)
- `ExplorationPlanner.findExplorationWaypoint(...)`: When no resources exist within 24 blocks (`findTargets().isEmpty()`), Ahmet does not freeze; he rotates across 8 directions (radii 8–18 blocks) exploring within `max-distance-from-home: 32` outside the protected house.
- **In-Transit Target Spotting**: Scans for targets on every tick while walking to exploration waypoints; as soon as a tree/resource enters view, exploration navigation halts immediately and harvesting begins.

### G. Multi-Step Safe Home Return (Latest Addition)
- `ExplorationPlanner.findStepTowardsHome(...)`: When Ahmet is far from home (or returning home when idle after delivery/cancellation), calculates 16-block intermediate waypoints along the direct vector towards home on safe ground.
- Obstacle avoidance: Rotates angle offsets (0°, ±25°, ±45°) on successive retry attempts if straight lines are obstructed. Stops cleanly upon arrival within 2.5 blocks of home.
- `/ahmet home` / `/ahmet return` command and Turkish chat intent (`"Ahmet, eve dön"`, `"Ahmet, evine git"`) via `isReturnHome`.
- Updated `navigate()` check: destinations moving Ahmet closer to home or targeting chest/home are never blocked even if Ahmet was outdoor-summoned beyond 32 blocks.

---

## 3. Test Suites & Verification Evidence

- **Java Maven Unit Tests**: **72 tests, 0 failures** (`mvn test` in `paper-plugin/`).
  - Also covers `TaskAuthorizationTest` (2), `SafeGroundPolicyTest` (1), `NavigationTargetTest` (1), `IncrementalTargetScannerTest` (2), `TaskStateSaveFailureTest` (1), and `DecisionRetryTest` (1), in addition to the task, journal, catalog, and navigation suites.
- **Node Brain Unit Tests**: **15 tests, 0 failures** (`npm test` in `brain/`).
  - Covers Turkish chat parsing, return-home intent (`isReturnHome`), JEV model contract, OpenAI GPT-6 Luna contract, memory store, decision fallbacks, bounded item IDs, and action allowlists.
- **Distribution Packages**:
  - `server-live/plugins/VillagerAhmet.jar` (SHA-256: `400346320a4d55f36e12c608b0e12749db7aeebb80059d22f980f620be6690c7`)
  - `dist/villager-ahmet-plugins-paper-1.21.6.zip` (SHA-256: `39d182e9b81a5319affa07edf1b3883e1c14573ecb13a893bf189884cf67bfe0`)
  - Validated by `dist/SHA256SUMS.txt`.

---

## 4. Live Server Environment (`server-live`)
- **Server Version**: Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138.
- **Configured Locations**:
  - Home: `world (4, 76, 125)`
  - Protected Home: `world (5, 73, 149)` (radius 10)
  - Delivery Chest: `world (3, 73, 144)`
- **Server Startup Command**:
  ```bash
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/maven/bin:/Users/aselekoglu/.local/bin:$PATH"
  AHMET_SERVER_DIR=server-live ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
  ```
- **Rules of Engagement for Restarts**:
  - Check `/list` before restarting. NEVER restart while players are connected.
  - Shut down using `stop` command so all chunk I/O terminates cleanly and world dimensions save.

---

## 5. Codex Review Instructions (sol 6.1)

Historical review prompt for **sol 6.1** (the claims and deployment hashes above describe earlier states and must not be treated as evidence for current source or live packages):
1. **Architectural Coherence & Concurrency Safety**: Verify that all Bukkit/Citizens mutations and reads remain strictly on the server main thread, and all Node/Brain HTTP communications remain non-blocking with bounded timeouts.
2. **State & Transaction Atomicity**: Audit `OperationJournal`, `OperationRecovery`, and `CargoDepositPlanner` for edge cases in failure recovery or chest capacity exhaustion.
3. **Exploration & Navigation Robustness**: Review `ExplorationPlanner.java` algorithms (`findExplorationWaypoint` and `findStepTowardsHome`) for pathfinding efficiency, boundary limits, and obstacle avoidance.
4. **Security & Input Validation**: Ensure bounded typed actions and strict validation of player chat and task inputs without any command injection or arbitrary coordinate passthrough.
5. **Next Roadmap Objectives**:
   - Live gameplay validation of wandering exploration and multi-step home return with online player.
   - Enhanced visual feedback / particle effects or arm swinging during harvesting.
   - Clean up duplicate Citizens NPC template records if any exist in the saved world registry.


## Remediation notes (2026-10-12)

- Current source changes add bounded incremental scanning, stricter recovery and authorization, safe-ground hazards, plain-stack deposit planning, and checked temporary-file YAML persistence. These changes are not evidence of deployment or live-world verification.
- Verification on 2026-10-12: Java 21 Maven package passed with 72 tests, Node tests passed 15/15, and mock HTTP smoke passed. The rebuilt JAR remains local and is not deployed to `server-live`.
- Animation/particles and duplicate Citizens registry-record cleanup remain follow-ups. No saved registry files were edited.
- The manual acceptance matrix remains authoritative; do not mark any newly affected live-world or forced-crash scenario complete without observing it.
