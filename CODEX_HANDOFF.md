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
### H. Tree Canopy Navigation Recovery & Safe Leaf Clearing
- `HarvestApproachPlanner`: Selects walkable approach block before attempting block mutations.
- `ObstructionPolicy`: Restricts clearance to natural, non-persistent, non-waterlogged, non-hazard leaves.
- `DescentSimulator`: Simulates safe vertical landing ($\le 2$ blocks drop) before permitting removal of support leaves.
- `OperationJournal.Clear`: Zero-cargo atomic journal operation ensuring fail-closed recovery on crash.

### I. AgentController & Preconditions Harness
- Modular controller model (`AgentState`, `AgentGoal`, `OfferedAction`, `ActionBudget`, `ExecutionResult`). The production task loop remains in `AhmetPlugin`; observation-based `evaluateGoal` is harness-only and not wired into production decisions.
- Deterministic server main-thread validation via `ActionPreconditions` before any world/inventory mutation.
- `ProtectionHook` SPI and `CompositeProtectionHook` providing fail-closed protection checks in production and harness paths.
- Structured player help requests (`BLOCKED_AWAITING_HELP`) when route retries or safe clearance are exhausted.

---

## 3. Test Suites & Verification Evidence

- **Java Maven Unit Tests**: **125 tests, 0 failures** (`mvn test` in `paper-plugin/`).
  - Covers `AgentEvaluationHarnessTest` (7), `AgentControllerTest` (3), `AgentModelsTest` (4), `ActionPreconditionsTest` (5), `ProtectionHookTest` (5), `ObstructionPolicyTest` (9), `DescentSimulatorTest` (7), `HarvestApproachPlannerTest` (3), `OperationJournalTest` (8), `OperationRecoveryTest` (6), `WoodTaskTest` (12), `ExplorationPlannerTest` (9), `CargoDepositPlannerTest` (6), `ClearTransactionTest` (2), `TaskAdmissionTest` (2), `ResourceCatalogTest` (3), and all existing unit suites.
- **Node Brain Unit Tests**: **16 tests, 0 failures** (`npm test` in `brain/`).
  - Covers Turkish chat parsing, return-home intent, JEV model contract, OpenAI GPT-6 Luna contract, memory store, decision fallbacks, bounded item IDs, action allowlists, and fallback leaf clearing protection.
- **Distribution Packages**:
  - `server-live/plugins/VillagerAhmet.jar` (commit `775ca1e`, pushed to `origin/main`; SHA-256: `57172d57df10eb13821fd9a907025d8422c3b7bb3dd3a5ec90618c7223eca83a`). Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138 + Java 21 started with `Done (10.035s)`; brain is listening at `127.0.0.1:3197`. `/ahmet status`: IDLE, cargo 0, chest bound, recovery unblocked; NPC id 1 restored but unspawned (`NPC: yok`). Old startup/shutdown retired the pre-existing clear journal before this startup, so replay of those records was not exercised here. (Note: the 2026-10-16 source fixes in the working tree/next commit have not yet been deployed to `server-live`.)
  - `dist/villager-ahmet-plugins-paper-1.21.6.zip` (SHA-256: `a4743a1d5dbe053f17385ab7a512013b65b1555e05b02e2d088b99566252970d`)
  - Validated by `dist/SHA256SUMS.txt`.

---

## 4. Live Server Environment (`server-live`)
- **Server Version**: Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138.
- **Configured Locations**:
  - Home: `world (11, 71, 153)`
  - Protected Home: `world (5, 73, 149)` (radius 10)
  - Delivery Chest: `world (3, 73, 144)`
- **Server Startup Command**:
  ```bash
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/maven/bin:/Users/aselekoglu/.local/bin:$PATH"
  AHMET_SERVER_DIR=server-live ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
  ```
- **Rules of Engagement for Restarts**:
  - Shut down using graceful `stop` (or clean SIGTERM save) so all chunk I/O and journals flush cleanly and world dimensions save.
  - Per explicit user instruction, graceful restarts are permitted even when players are connected; connected players alone do not block an authorized deployment restart.

---

## 5. Codex Review Instructions (sol 6.1)

Historical review prompt for **sol 6.1** (the claims and deployment hashes above describe earlier states and must not be treated as evidence for current source or live packages):
1. **Architectural Coherence & Concurrency Safety**: Verify that all Bukkit/Citizens mutations and reads remain strictly on the server main thread, and all Node/Brain HTTP communications remain non-blocking with bounded timeouts.
2. **State & Transaction Atomicity**: Audit `OperationJournal`, `OperationRecovery`, and `CargoDepositPlanner` for edge cases in failure recovery or chest capacity exhaustion.
3. **Exploration & Navigation Robustness**: Review `ExplorationPlanner.java` algorithms (`findExplorationWaypoint` and `findStepTowardsHome`) for pathfinding efficiency, boundary limits, and obstacle avoidance.
4. **Security & Input Validation**: Ensure bounded typed actions and strict validation of player chat and task inputs without any command injection or arbitrary coordinate passthrough.
5. **Next Roadmap Objectives**:
   - First open P1 item: Citizen NPC schedule (wake, wander, work, sleep at bed and village home).
   - Live gameplay validation of wandering exploration and multi-step home return with online player.
   - Enhanced visual feedback / particle effects or arm swinging during harvesting.
   - Clean up duplicate Citizens NPC template records if any exist in the saved world registry.


## Remediation notes (2026-10-12 .. 2026-10-16)

- Source changes add bounded incremental scanning, stricter recovery and authorization, safe-ground hazards, plain-stack deposit planning, checked temporary-file YAML persistence, truthful task-admission ordering (`BUSY` vs `RECOVERY_REQUIRED` when committed harvest journal entries accompany carried cargo), multi-material deposit warning fix, and cardinal/inner-ring harvest approach + arrival handling.
- Verification on 2026-10-16: Java 21 Maven package passed with **125/125** tests, Node tests passed **16/16**, and the disposable Paper 1.21.6 runtime probe (`scripts/fixtures/RuntimeCommonResourceProbe.java`) verified `/ahmet items` console queries, all 11 source block drops (+ `COARSE_DIRT` negative control), scan behavior and invocation timing (the earlier probe used a synchronous loop, the corrected independent rerun subsequently verified 74 separate scheduler ticks plus next-tick invalidation; see VALIDATION_RESULTS.md), and end-to-end bounded collection + Citizens villager walking (`teleportDetected=false`) + virtual cargo + chest delivery across all 7 common-resource groups (`dirt`, `sand`, `red_sand`, `cobblestone`, `coal`, `raw_iron`, `raw_gold`, depositing 11/11 items into the bound chest) against the mock brain.
- Animation/particles and duplicate Citizens registry-record cleanup remain follow-ups. No saved registry files were edited.
- The manual acceptance matrix remains authoritative; do not mark any newly affected live-world or forced-crash scenario complete without observing it.
