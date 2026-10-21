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
- Final four-entry ZIP was rebuilt locally and verified after the README correction: SHA-256 `5781ea3489fef9f5828f0c21cc980a414ed9f8c8602a1057b9ce3adb0788c207`; embedded core remains `5e8395902b24f374b1176e98cc14957bccde63d07ba9795da940d9ef1500f8a3`, and pinned Citizens remains `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`. The local `dist/` package is gitignored; no GitHub release asset is claimed. Source/docs commit `6b015f1714be8a0a986516132436a28e1341408f` was pushed normally and matched remote read-back. The exact core was installed; Paper and Node health/status and save completion were read back. The server was already off and prior shutdown cause/time are unknown, so no graceful stop is claimed. Pending JOURNAL/recovery holds were injected and do not establish forced-crash durability. No human gameplay or M3-path live provider call occurred; human, provider, store-inspection and forced-crash acceptance remain open. M3 source/runtime/package/publication/deployment is complete; M4–M10 remain open.

### M4 presentation source, runtime and local package — 2026-10-21
- Reviewed production source is commit `8572c61afb3591130e4499604f26defc10c1db2f`; exact M4 core SHA-256 `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c`. The Java package passed **270/270** tests (0 failures/errors/skips), including **11/11** focused presentation tests. Node remains at the previously recorded **43/43** plus mock HTTP smoke; Node was not rerun for this Java/runtime increment.
- The calibrated disposable Paper/Citizens presentation probe passed its normal r10 run (exit 0, all three dimensions saved, no ERROR lines, terminal `AHMET_PRESENTATION_RUNTIME_PROBE_PASS`). Normal harvest/deposit, injected swing failure, injected sound failure, and full-chest hold/retry each ultimately delivered 1 item (collected 1/deposited 1/cargo 0); the full-chest hold preserved cargo 1 with no failed-deposit cue until capacity returned. Journal and navigation were clear, recovery was false, cues totaled two, and normal teleports were zero. Recorded paths were 16.07/17.45/18.97/18.97 blocks; maximum sampled step was 0.20 blocks/tick rounded. Separate face-delegate and sound-invariant controls each ended in their expected terminal FAIL with no final PASS; these are negative controls, not positive acceptance.
- The corrected four-entry M4 ZIP was rebuilt and verified: SHA-256 `6846225df2d99b40142f1ca377a014a964d40cf5fe4b3093745f21c5871691a0`. It contains core `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c`, pinned Citizens `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`, and Turkish README `6aaa7aad02f3b79e8c7f83fb629ca7b4f3360f5fb9e0f09800c2cf32d0b84feb`; all four entries, CRCs, inner JAR hashes and outer ZIP hash were verified. `dist/` remains intentionally Git-ignored; no GitHub release asset is claimed.
- Runtime presentation used synthetic player/LOS proxies and an embedded mock chat bridge (two HTTP requests, zero live providers). The shared per-UUID public/private one-second cooldown can suppress a private reply after public speech. Look chunk-preflight counts covered active task/cargo only; face and LOS were zero across job/return. The probe does not measure 20-tick scan cadence. Client packet delivery, visible animation, sound quality, human multiplayer, live providers and forced-crash durability remain unverified.
- M4 source, disposable server-side runtime gate, corrected local package, source/docs publication, and live startup/save read-back are complete. Source/docs commit `620b97efa4448aee5e6682fc3d2adb9f99b7b9e4` was pushed with the normal Git CLI and matched the remote SHA. The verified four-entry ZIP is `6846225df2d99b40142f1ca377a014a964d40cf5fe4b3093745f21c5871691a0`; exact core `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c` is installed. Paper started in 11.243 seconds with no errors; Node health was `ok` with configured `jev-preview`/JEV and LLM chat modes. This is configuration/startup evidence, not a fresh provider-version resolution. Console read-back showed zero players, IDLE, cargo 0, chest bound, bed unbound, and NPC unspawned; `save-all flush` acknowledged `Saved the game`. No collect/summon/home/bed change, model request, or world experiment was issued. The server was already off before installation and its prior stop cause/time are unknown; no graceful prior shutdown is claimed. Human/client, provider, forced-crash and production-store acceptance remain open; M5–M10 remain open.

### M5 allowlisted transactional crafting source, runtime and local package — 2026-10-21
- Reviewed production source implements allowlisted, transactional, chest-centric crafting for `minecraft:oak_planks` and `minecraft:stick` at quantities `4..64` (multiples of 4, no rounding). Exact M5 core JAR SHA-256: `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f`.
- The offline Maven package passed **317/317** tests (0 failures/errors/skips), and the Node brain passed **46/46** tests (0 failures).
- The disposable Paper 1.21.6/Citizens 2.0.41 runtime probe (`/private/tmp/ahmet-crafting-runtime-20261021-r13`, probe JAR SHA-256 `a1258bbfb357f094aed825fc881f25f078abf0fbd4bb35c62bcdf7697671ec21`) passed all 6 stages (`AHMET_CRAFTING_RUNTIME_PROBE_PASS stages=6 normalTeleports=0`, exit 0, all 3 dimensions saved cleanly, 0 ERROR lines):
  - Stage 1 (`oak_planks_walk_craft`): `/ahmet craft oak_planks 8` walked 5.01 blocks (`maxStep=0.20`, `teleports=0`) to the 54-slot double chest, consumed `OAK_LOG x1` (slot 0) + `STRIPPED_OAK_LOG x1` (slot 30, leaving `x1`), placed `OAK_PLANKS x8` in slot 0, and preserved `DIAMOND x5` in slot 40.
  - Stage 2 (`stick_walk_craft_custom_preserved`): `/ahmet craft stick 8` walked 5.01 blocks (`maxStep=0.20`, `teleports=0`), preserved custom `"Heirloom Plank"` (`OAK_PLANKS x1` in slot 2), consumed plain `OAK_PLANKS x2` (slot 5) + `BIRCH_PLANKS x2` (slot 35, leaving `x1`), and produced `STICK x8`.
  - Stage 2B (`chat_craft_item_and_events`): `/ahmet ask 8 meşe tahtası üret` via loopback mock HTTP bridge admitted `craft_item` (`minecraft:oak_planks`, amount 8), walked 5.01 blocks (`maxStep=0.20`, `teleports=0`), converted `OAK_LOG x2` into `OAK_PLANKS x8`, and emitted `task_started` and `task_delivered`.
  - Stage 3 (`refusals_and_arrival_shortage`): command refusals for non-batch count (`6`), insufficient inputs (`16` with 1 log), full-chest output capacity, concurrent busy craft command, and non-owner cancel; operator `/ahmet cancel` while walking left chest untouched; arrival-time shortage (chest mutated from 2 logs to 1 while walking) aborted cleanly with `task_cancelled` and `fakeDeliveredEvent=false`.
  - Stage 4 (`journal_recovery_and_conflict`): verified injected startup reconciliation for `APPLY_REMAINING` (`oak_planks` x8 applied once), `COMPLETE_ALREADY_APPLIED` (`stick` x8 reconciled without double-crediting), and `CONFLICT` (tampered slot locked `recoveryBlocked=true` and retained `pendingCraft`). Note explicitly that Stage 4 uses injected journal/task states in a disposable server and is not a forced-crash (`kill -9`) test.
  - Stage 5 (`gather_oak_log_then_craft_chain`): end-to-end regression on the exact M5 core: `/ahmet collect oak_log 1` harvested a leaf-marked `OAK_LOG` and deposited `OAK_LOG x1` into the bound double chest, followed by `/ahmet craft oak_planks 4` (`OAK_PLANKS x4`) and `/ahmet craft stick 4` (`OAK_PLANKS x2` + `STICK x4` remaining), with `normalTeleports=0`.
- Four-entry M5 ZIP package (`dist/villager-ahmet-plugins-paper-1.21.6.zip`) rebuilt and verified: ZIP SHA-256 `fa656459ec6bb47305dbeecd885141d97a17b998007bb8151b3cade978d25a79`; core JAR `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f`; pinned Citizens `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`; Turkish README `da5c616dfb18c4f14df3a1868843daeca2eb9f239f3cdf0d089b497fe588aac9`.
- Published M5 commits `d187abd29ad593cfdef4b3721f90f6111bb46e2c` and `6bbad145abbce242cc7564cd50904249fce706e3` to `origin/main`. Verified stopped-server backup `12240fb1a126f0a367bdf8dee8b59556b7b0ba7866832b0067497d8ca58a16bc` (165 files / 29,900,655 bytes, mode `0600`), installed exact core `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f` on `server-live`, and verified startup (`Done (9.849s)!`, 0 errors), Node `/health` (`ok=true`, configured `jev-preview`), `/ahmet status` (`IDLE`, cargo 0, chest bound, NPC unspawned), and `save-all flush` (`Saved the game`).
- Human client acceptance, live provider behavior through current path, production-store inspection, and forced-crash durability remain unverified.

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

### M. Allowlisted Transactional Crafting (M5)
- **Allowlisted Recipes**: Supports `minecraft:oak_planks` (1 oak log/wood/stripped variant -> 4 `OAK_PLANKS`) and `minecraft:stick` (2 plain planks across 12 wood species -> 4 `STICK`). Output amounts are restricted to `4..64` divisible by 4; non-multiples fail closed without rounding. Items with custom names, lore, or metadata are ignored by `CraftingPlanner` to preserve player heirlooms.
- **Unified Task Model**: Shared `WoodTask` lifecycle (`Kind.GATHER` vs. `Kind.CRAFT`), preserving task owner, job generation, route ownership, and admission rules. Admitted via `/ahmet craft` or `/api/chat` (`craft_item`).
- **Chest-Centric Pathfinding**: Ahmet walks to the bound chest using Citizens pathfinding without teleporting (`totalDistanceWalked >= 3.0`, `maxStep <= 0.22`, `teleports == 0`). A full 54-slot double-chest inventory view (`Chest.getInventory()`) is used.
- **Arrival Re-Validation & Safe Abort**: Inputs and capacity are re-validated upon arrival at the chest; if inputs were taken during transit, the task aborts safely (`abortCraftTask`) with `task_cancelled` without touching items.
- **Write-Ahead Journal v4 & Crash Recovery**: `OperationJournal.Craft` records exact before/after slots in `operations.json` before modifying chest items. `OperationRecovery.reconcilePendingCraft` resolves startup states into `APPLY_REMAINING`, `COMPLETE_ALREADY_APPLIED`, `FINALIZE_ALREADY_COMPLETED`, or `CONFLICT` (which sets `recoveryBlocked = true` and preserves journal records). `canCancelOrReturnHome()` guards against premature cancellation while operations are pending.

---

## 3. Test Suites & Verification Evidence

- **Java Maven Unit Tests**: latest reviewed source **317 tests, 0 failures** (`mvn -B -ntp -o test` in `paper-plugin/`). Earlier dated counts below remain historical snapshots.
  - Covers `AgentEvaluationHarnessTest` (7), `AgentControllerTest` (3), `AgentModelsTest` (4), `ActionPreconditionsTest` (6), `ProtectionHookTest` (5), `ObstructionPolicyTest` (9), `DescentSimulatorTest` (7), `HarvestApproachPlannerTest` (3), `OperationJournalTest` (16), `OperationRecoveryTest` (16), `WoodTaskTest` (24), `ExplorationPlannerTest` (12), `CargoDepositPlannerTest` (6), `CraftingPlannerTest` (12), `ClearTransactionTest` (2), `TaskAdmissionTest` (4), `TaskRequestTest` (5), `ResourceCatalogTest` (3), and all existing unit suites.
- **Node Brain Unit Tests**: latest reviewed source **46 tests, 0 failures** (`node --test test/*.test.mjs` in `brain/`). Earlier dated counts below remain historical snapshots.
  - Covers Turkish chat parsing, return-home intent, craft item intent parsing & validation, JEV model contract, OpenAI GPT-6 Luna contract, memory store, decision fallbacks, bounded item IDs, action allowlists, and fallback leaf clearing protection.
- **Distribution Packages**:
  - Historical private server package: commit `775ca1e`, SHA-256 `57172d57df10eb13821fd9a907025d8422c3b7bb3dd3a5ec90618c7223eca83a`; its 2026-10-12 startup does not verify the current schedule source. The exact current JAR was installed on 2026-10-18 and Paper startup plus `save-all` were verified; `/ahmet status` reported NPC id restored in Citizens but unspawned (`NPC: yok`). See the live deployment record in `docs/VALIDATION_RESULTS.md`.
  - Historical M1 package `dist/villager-ahmet-plugins-paper-1.21.6.zip` SHA-256: `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8`; it predates M2 and is not the M2 release artifact. It contains the Paper plugin and pinned Citizens dependency, not the Node brain.
  - Final M2 package `dist/villager-ahmet-plugins-paper-1.21.6.zip` has four verified entries and SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`; the embedded core JAR matches `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`. No config secrets or test ZIP are included. Published source commit: `ba29c68916562f922b3bae085c72b51f345030c0` (tree `30d87eaf4c82778cfac5d4b9b87112129b600b76`). Live deployment/read-back completed; see `docs/VALIDATION_RESULTS.md`.
  - Final M5 package `dist/villager-ahmet-plugins-paper-1.21.6.zip` has four verified entries and SHA-256 `fa656459ec6bb47305dbeecd885141d97a17b998007bb8151b3cade978d25a79`; the embedded core JAR matches `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f`. Pinned Citizens SHA-256: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`; Turkish README SHA-256: `da5c616dfb18c4f14df3a1868843daeca2eb9f239f3cdf0d089b497fe588aac9`. Disposable crafting probe JAR SHA-256: `a1258bbfb357f094aed825fc881f25f078abf0fbd4bb35c62bcdf7697671ec21`.

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
