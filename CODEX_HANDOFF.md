# Codex Handoff — Villager Ahmet

## 1. Project Overview & Architecture
Villager Ahmet is an autonomous, AI-driven assistant in Minecraft Java Edition running on **Paper 1.21.6 (build 48)** with **Citizens 2.0.41 (build 4138)** and **Java 21**, coordinated by a local **Node.js (v22)** brain daemon (`127.0.0.1:3197`).

### Architectural Division of Responsibility
- **Java Paper Plugin (`paper-plugin/`)**: Authoritative owner of world mutations, entity pathfinding, item transactions, task state, write-ahead journaling, and safe boundaries. All Bukkit/Citizens calls stay on the server main thread.
- **Node Brain Daemon (`brain/`)**: Language understanding, structured task extraction, conversational memory, and decision candidate selection. Strictly distinguishes **TypeSafe JEV** (`POST https://api.typesafe.ai/v1/systemone` using model `jev-latest`) for bounded decision selection from **OpenAI GPT-6 Luna** (`POST https://api.openai.com/v1/chat/completions`) for conversational chat.
- **Private LAN Network Policy**: Operating on a private LAN with `online-mode=false` (to accommodate operator's offline TLauncher client profile) and whitelist enabled. Strictly private; never port-forwarded or exposed publicly.

### Deterministic Citizens schedule
- `ScheduleAdapter` runs the real Citizens villager through WAKE `[0,1000)`, WORK `[1000,9000)`, WANDER `[9000,12000)`, and SLEEP `[12000,24000)` using world time modulo 24,000 ticks. WORK is idle availability at home, not automatic harvesting.
- Operators bind an existing nearby bed with `/ahmet bed`; only its location is persisted. Ahmet walks to a safe adjacent standing cell before native sleep. Invalid, occupied, stale, wrong-world, or unreachable beds fail closed to home and expose the current reason in `/ahmet status`.
- Jobs, cargo, recovery blocks, and outstanding journal work immediately preempt schedule movement/sleep. Routine schedule movement is bounded Citizens pathfinding with no teleport; task navigation defaults are preserved. Schedule enablement defaults to `true` (`options.schedule-enabled`).

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
- Live in-game verified: Ahmet autonomously walked to the bound double chest in the test arena and deposited carried birch logs cleanly into slot 27 of the empty second half.

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

### J. Deterministic Citizens Schedule
- Schedule policy and adapter are integrated with the production task loop and expose phase/activity/reason in `/ahmet status`; `/ahmet bed` binds an existing bed.
- Unit coverage includes time boundaries, eligibility, safe bounded wander, task/cargo/recovery/journal preemption, bed state transitions, current failure reporting, and retry cooldowns. Runtime-probe results and the live-play acceptance boundary are recorded in the docs.

### K. Bounded player/session memory (M1)
- The Node brain stores a bounded profile and deterministic session summaries per actor UUID. Familiarity is based only on turn counts: new (<2), familiar (2–9), established (10+); it is never authority or permission.
- A 30-minute idle gap starts a session; the current summary and up to five previous summaries of at most 1,000 characters each are retained. Summaries contain discussion snippets, typed task requests, and separately labeled verified events; they do not infer arbitrary personal facts and use no additional LLM call.
- Schema v1 migrates to v2 while preserving conversation history and audit rows. Actor maps are prototype-safe; limits are 200 actors, 500 combined events/audit rows, 20 stored turns per actor, and six turns in model history.
- Only `task_started`, `task_cancelled`, and `task_delivered` with a valid actor reach verified model context. Unknown events and old chat audit rows remain separate. Model context is untrusted data and does not alter task validators, task/cargo ownership, or chest gates.
- Local Node suite is **28/28**. M1 source is published at `74f61d580e74d1c942b3f2badd7f498324212339`; the current Node process started and its health/configuration and idle status were read back. No live provider call, human gameplay with the new memory, or production-store migration inspection was performed.

---

## 3. Test Suites & Verification Evidence

- **Java Maven Unit Tests**: latest source **155 tests, 0 failures** (`mvn package` in `paper-plugin/`; controller log `local scratch evidence`). Earlier dated counts below remain historical snapshots.
  - Covers `AgentEvaluationHarnessTest` (7), `AgentControllerTest` (3), `AgentModelsTest` (4), `ActionPreconditionsTest` (5), `ProtectionHookTest` (5), `ObstructionPolicyTest` (9), `DescentSimulatorTest` (7), `HarvestApproachPlannerTest` (3), `OperationJournalTest` (8), `OperationRecoveryTest` (6), `WoodTaskTest` (12), `ExplorationPlannerTest` (9), `CargoDepositPlannerTest` (6), `ClearTransactionTest` (2), `TaskAdmissionTest` (2), `ResourceCatalogTest` (3), and all existing unit suites.
- **Node Brain Unit Tests**: current source **28 tests, 0 failures** (`node --test test/*.test.mjs` in `brain/`). Earlier dated counts below remain historical snapshots.
  - Covers Turkish chat parsing, return-home intent, JEV model contract, OpenAI GPT-6 Luna contract, memory store, decision fallbacks, bounded item IDs, action allowlists, and fallback leaf clearing protection.
- **Distribution Packages**:
  - Historical private server package: commit `775ca1e`, SHA-256 `57172d57df10eb13821fd9a907025d8422c3b7bb3dd3a5ec90618c7223eca83a`; its 2026-10-12 startup does not verify the current schedule source. The exact current JAR was installed on 2026-10-18 and Paper startup plus `save-all` were verified; `/ahmet status` reported NPC id restored in Citizens but unspawned (`NPC: yok`). See the live deployment record in `docs/VALIDATION_RESULTS.md`.
  - Rebuilt M1 package `dist/villager-ahmet-plugins-paper-1.21.6.zip` SHA-256: `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8`; four entries and archive/embedded hashes verified. It contains the unchanged Paper JAR SHA-256 `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1` and pinned Citizens JAR SHA-256 `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`; the Node brain is not bundled. The JAR’s prior disposable acceptance evidence is unchanged. The separate Node brain is deployed from the published source.
  - Validated by `dist/SHA256SUMS.txt`. This archive contains the Paper plugin and Citizens dependency, not the Node brain; it does not include M1 memory code.

---

## 4. Private Server Environment
- **Server Version**: Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138.
- World locations and host addresses are omitted from public documentation. The supported private LAN configuration keeps the whitelist enabled and the brain bound to loopback.
- **Generic startup command**:
  ```bash
  export JAVA_HOME="<Java 21 runtime>"
  export PATH="$JAVA_HOME/bin:<Maven runtime>/bin:$PATH"
  AHMET_SERVER_DIR="<private server directory>" ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
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
   - Live gameplay validation of schedule, wandering exploration and multi-step home return with an online player.
   - Enhanced visual feedback / particle effects or arm swinging during harvesting.
   - Clean up duplicate Citizens NPC template records if any exist in the saved world registry.


## Remediation notes (2026-10-12 .. 2026-10-16)

- Source changes add bounded incremental scanning, stricter recovery and authorization, safe-ground hazards, plain-stack deposit planning, checked temporary-file YAML persistence, truthful task-admission ordering (`BUSY` vs `RECOVERY_REQUIRED` when committed harvest journal entries accompany carried cargo), multi-material deposit warning fix, and cardinal/inner-ring harvest approach + arrival handling.
- Verification on 2026-10-16: Java 21 Maven package passed with **125/125** tests, Node tests passed **16/16**, and the disposable Paper 1.21.6 runtime probe (`scripts/fixtures/RuntimeCommonResourceProbe.java`) verified `/ahmet items` console queries, all 11 source block drops (+ `COARSE_DIRT` negative control), scan behavior and invocation timing (the earlier probe used a synchronous loop, the corrected independent rerun subsequently verified 74 separate scheduler ticks plus next-tick invalidation; see VALIDATION_RESULTS.md), and end-to-end bounded collection + Citizens villager walking (`teleportDetected=false`) + virtual cargo + chest delivery across all 7 common-resource groups (`dirt`, `sand`, `red_sand`, `cobblestone`, `coal`, `raw_iron`, `raw_gold`, depositing 11/11 items into the bound chest) against the mock brain.
- Animation/particles and duplicate Citizens registry-record cleanup remain follow-ups. No saved registry files were edited.
- The manual acceptance matrix remains authoritative; do not mark any newly affected live-world or forced-crash scenario complete without observing it.

## Schedule release status (2026-10-18)

- Previous schedule checkpoint (superseded artifact): local JAR SHA-256 `d91ee295b59a46353e69154ac6a3969bc710f5b01adacea0f37d0c22ed0cd95e` with 153 Java tests. Its disposable common-resource, fresh schedule, and same-world restart results are historical evidence for that JAR only.
- Current schedule fix: 155 Java tests pass. The new JAR `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1` passed complete fresh and same-world restart schedule probes plus common-resource runtime acceptance. The outside-radius WANDER return took 48 ticks (8.855 blocks walked to within 2.5 blocks of home); movement probes recorded zero production teleports. On 2026-10-18 this exact JAR was installed on the private server copy; Paper startup and `save-all` were verified. The read-back showed NPC id 1 restored but no spawned entity, so no live NPC movement was observed. Live player schedule behavior and live provider calls remain unverified.
