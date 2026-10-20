# Codex Handoff — Villager Ahmet

## 1. Project Overview & Architecture
Villager Ahmet is an autonomous, AI-driven assistant in Minecraft Java Edition running on **Paper 1.21.6 (build 48)** with **Citizens 2.0.41 (build 4138)** and **Java 21**, coordinated by a local **Node.js (v22)** brain daemon (`localhost:3197`).

### Architectural Division of Responsibility
- **Java Paper Plugin (`paper-plugin/`)**: Authoritative owner of world mutations, entity pathfinding, item transactions, task state, write-ahead journaling, and safe boundaries. All Bukkit/Citizens calls stay on the server main thread.
- **Node Brain Daemon (`brain/`)**: Language understanding, structured task extraction, conversational memory, and decision candidate selection. Strictly distinguishes **TypeSafe JEV** (`POST https://api.typesafe.ai/v1/systemone`, default model alias `jev-preview`, with the resolved version read from response metadata) for bounded decision selection from **OpenAI GPT-6 Luna** (`POST https://api.openai.com/v1/chat/completions`) for conversational chat.
- **Private LAN Network Policy**: A private LAN that accepts offline clients may use `online-mode=false` with whitelist enabled. Offline mode allows username spoofing; use only on a trusted LAN, never port-forward, and restore online authentication before any public exposure.

### JEV preview provider acceptance — 2026-10-19
- One synthetic TypeSafe choice call using alias `jev-preview` returned `source=jev`, resolved model `jev-1.14.0`, and the offered `wait` action. The observed call took **558 ms**; this single sample is not a latency benchmark.
- The copied `scripts/run.sh` launcher was checked without an explicit process-level model override: a synthetic dotenv value of `jev-latest` produced health configuration `jev-preview` with mock chat and decision modes. This proves copied-source launcher precedence, not a provider call.
- In the fresh isolated release worktree, Node passed **43/43** tests and `scripts/smoke.sh` passed in mock mode. These checks do not substitute for the single live provider call above.
- This release changes only Node JEV selection/configuration/error handling and its launcher default. The installed M2 core JAR and four-entry distribution are unchanged (SHA-256 `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b` and `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`, respectively). Publication and live startup are verified below.
- The provider evidence above does not prove JEV invocation through Paper, Minecraft task behavior, human acceptance, OpenAI behavior, or production-memory-store migration. The deployment read-back below verifies startup and configured modes only; the remaining checks are open.

### JEV preview publication and live startup — 2026-10-19
- Published source commit `c8f2777a934cd2355844a0685aacadb1fa715f85` contains the reviewed Node JEV change from commit `a3e82ffbf0372fd2f979ca1fc7530fa737b76962`. The deployed Node source and launcher match the published source byte for byte. The installed M2 core JAR and four-entry distribution remain unchanged (SHA-256 `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b` and `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`).
- Paper started in **7.834 seconds**. Health reported configured `jev-preview`, decision mode `jev`, chat mode `llm`, and `ok`. Status read-back was IDLE, cargo 0, chest bound, bed unbound, recovery unblocked, NPC unspawned, and no connected players. All three dimensions were flush-saved; startup reported 0 errors.
- No provider request or human-player interaction occurred during deployment. The separate single TypeSafe call above remains the only live provider proof. Since the NPC was unspawned, this read-back does not demonstrate walking or Minecraft task behavior. Human, OpenAI, and production-memory-store acceptance remain open.

### M3 production goal policy final source/runtime — 2026-10-20
- Production goal selection is in the Paper task loop behind `options.production-goals-enabled: false`. It acts only within explicit authorized jobs and cannot create village needs, new jobs or permissions. Task ID, owner, generation, global step and owned route sequence are checked. Productive transitions cap at 32, then require safe return and delivery; obstruction clears are capped at 3 per route and 12 per job, separately from the 64-item request limit.
- Final core SHA-256 `5e8395902b24f374b1176e98cc14957bccde63d07ba9795da940d9ef1500f8a3` passed 259 Java tests; Node passed 43/43; mock smoke passed. Whole-source review marked all source gates addressed. In ordinary in-bound work, foreign Citizens routes are preserved and bounded to a 100-tick wait before a help attempt; mandatory home return or ready-cargo delivery may supersede such a route. The shared 30-second help cooldown can delay acceptance/message, and the waiter retries each distinct tick until acknowledgement while using one help sequence. Actual retry availability, active-task GIVE_UP cleanup, partial-delivery outcome reporting, and unloaded-bed fallback are covered.
- Final normal disposable goal probe passed: 96.028 blocks walked over 507 moving ticks, max sampled step 0.198 blocks/tick, zero normal teleports, 6 mock decisions, 1 mock chat and 0 providers. The real shared cooldown was preserved: first help attempt at 100 ticks was rejected, one acknowledgement arrived at 267 ticks, and explicit fixture release led to same-job harvest and delivery in 65 ticks. Default-off common-resource probe passed 7 groups/11 variants/11 deposited items with zero teleports. Fresh and same-world second-process schedule probes passed, restored saved identities and recorded zero normal teleports (four fixture setup teleports in fresh setup, none on restart).
- Final four-entry ZIP was rebuilt locally and verified after the README correction: SHA-256 `5781ea3489fef9f5828f0c21cc980a414ed9f8c8602a1057b9ce3adb0788c207`; embedded core remains `5e8395902b24f374b1176e98cc14957bccde63d07ba9795da940d9ef1500f8a3`, and pinned Citizens remains `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`. The local `dist/` package is gitignored; no GitHub release asset is claimed. Pending JOURNAL/recovery holds were injected and do not establish forced-crash durability. No human gameplay or M3-path live provider call occurred. Source publication and deployment/read-back are pending, so M3 remains open; M4–M10, human acceptance, provider acceptance and forced-crash acceptance remain open.

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
- Modular controller model (`AgentState`, `AgentGoal`, `OfferedAction`, `ActionBudget`, `ExecutionResult`). `AgentController` selects bounded production goals from server-authored facts in the task loop when the operator enables `options.production-goals-enabled`; the default is false. The policy acts only within already admitted jobs and cannot invent work or permissions.
- Task/job owner, ID, generation and global-step checks reject stale decisions. Exact owned-route checks prevent canceling another route; task, cargo, pending recovery/journal and schedule constraints retain priority. Productive transitions stop at 32 and force safe-home return/delivery; route/job clearing limits are 3/12, separate from the 64-output job limit. The M3 current-source evidence and release boundary are recorded above and in `docs/VALIDATION_RESULTS.md`.
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
- At the M1 checkpoint, the local Node suite was **28/28**. M1 source is published at `74f61d580e74d1c942b3f2badd7f498324212339`; the current Node process started and its health/configuration and idle status were read back. The latest combined source suite is **38/38**. No live provider call, human gameplay with the new memory, or production-store migration inspection was performed.

### L. Grounded read-only perception (M2)
- Paper captures a partial v1 snapshot on the main thread: four-block cardinal surface rays, at most 64 cells and eight material types, with no chunk loading. Chat and JEV observations carry the current job generation; obsolete chat callbacks are rejected before task, cancel, or home effects.
- Bound-chest inventory and player observations are disabled by default. Inventory requires explicit opt-in, an online same-world viewer with `ahmet.admin`, and the already-bound loaded chest within eight blocks; a double chest is read as up to 54 slots. Player observations require opt-in and emit only `near`/`nearby` bands for visible, non-invisible, same-world players. Snapshots contain no names, UUIDs, coordinates, item metadata, or NBT and have no action authority.
- The Node brain sanitizes perception at both the engine and provider boundaries; missing, stale, future-dated, malformed, or mismatched-generation input becomes unavailable. Perception adds no model calls or action types.
- Current reviewed core JAR SHA-256: `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`; offline Java package: **175/175**, Node suite: **38/38**. Disposable Paper 1.21.6/Citizens 2.0.41 runtime acceptance and captured synthetic chat/JEV request compatibility passed. The four-entry Paper/Citizens package is rebuilt and verified: ZIP SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`; it contains no config secrets or test ZIP. M2 source is published at `ba29c68916562f922b3bae085c72b51f345030c0` (reviewed tree `30d87eaf4c82778cfac5d4b9b87112129b600b76`), and live deployment/read-back completed with this package. Paper/Node health and idle status were read back; no provider was invoked, and human/player visibility acceptance remains open.

---

## 3. Test Suites & Verification Evidence

- **Java Maven Unit Tests**: latest reviewed source **175 tests, 0 failures** (`mvn package` in `paper-plugin/`). Earlier dated counts below remain historical snapshots.
  - Covers `AgentEvaluationHarnessTest` (7), `AgentControllerTest` (3), `AgentModelsTest` (4), `ActionPreconditionsTest` (5), `ProtectionHookTest` (5), `ObstructionPolicyTest` (9), `DescentSimulatorTest` (7), `HarvestApproachPlannerTest` (3), `OperationJournalTest` (8), `OperationRecoveryTest` (6), `WoodTaskTest` (12), `ExplorationPlannerTest` (9), `CargoDepositPlannerTest` (6), `ClearTransactionTest` (2), `TaskAdmissionTest` (2), `ResourceCatalogTest` (3), and all existing unit suites.
- **Node Brain Unit Tests**: latest reviewed source **38 tests, 0 failures** (`node --test test/*.test.mjs` in `brain/`). Earlier dated counts below remain historical snapshots.
  - Covers Turkish chat parsing, return-home intent, JEV model contract, OpenAI GPT-6 Luna contract, memory store, decision fallbacks, bounded item IDs, action allowlists, and fallback leaf clearing protection.
- **Distribution Packages**:
  - Historical private server package: commit `775ca1e`, SHA-256 `57172d57df10eb13821fd9a907025d8422c3b7bb3dd3a5ec90618c7223eca83a`; its 2026-10-12 startup does not verify the current schedule source. The exact current JAR was installed on 2026-10-18 and Paper startup plus `save-all` were verified; `/ahmet status` reported NPC id restored in Citizens but unspawned (`NPC: yok`). See the live deployment record in `docs/VALIDATION_RESULTS.md`.
  - Historical M1 package `dist/villager-ahmet-plugins-paper-1.21.6.zip` SHA-256: `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8`; it predates M2 and is not the M2 release artifact. It contains the Paper plugin and pinned Citizens dependency, not the Node brain.
  - Final M2 package `dist/villager-ahmet-plugins-paper-1.21.6.zip` has four verified entries and SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`; the embedded core JAR matches `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`. No config secrets or test ZIP are included. Published source commit: `ba29c68916562f922b3bae085c72b51f345030c0` (tree `30d87eaf4c82778cfac5d4b9b87112129b600b76`). Live deployment/read-back completed; see `docs/VALIDATION_RESULTS.md`.

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
