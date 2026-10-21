# Architecture and integration contract

## Source-of-truth separation

- **Paper plugin** is authoritative for villager position, chest binding, task execution, cargo and items placed in the chest. All mutable Bukkit/Citizens calls execute on the server main thread.
- **Node brain** is authoritative for chat history, event history, direct TypeSafe JEV calls, direct OpenAI chat calls and per-boundary decisions. It cannot directly invoke Minecraft commands.
- **JEV** is a *typed choice selector*, not the chat model, controller, plugin or pathfinder.
- **Chat model** can respond naturally but has no direct executable tools in this MVP. Tasks enter the engine via the separate validated parser only.
- **Item catalog** is generated at Paper startup from the pinned 1.21.6 `Material` registry and written to `plugins/VillagerAhmet/item-catalog-1.21.6.json`. `/ahmet items <name>` searches the same catalog. Each entry marks its executable capability separately: the full registry is recognition/validation data, not a grant to gather every item.

## Local REST protocol

All POSTs include `Authorization: Bearer <BRIDGE_TOKEN>` and JSON. Brain binds to `localhost:3197` by default. Every request is bounded. The Java plugin is client, Node brain is server.

### `POST /api/chat`

Request:

```json
{"actorUuid":"<player UUID>","actorName":"<player display name>","text":"Ahmet, 20 meşe odunu topla","context":{"generation":7,"busy":false,"chestBound":true,"perception":{"version":1,"available":false,"capturedAt":0,"generation":7,"phase":"UNKNOWN","blocks":{"sampledCells":0,"types":[]},"inventory":{"enabled":false,"available":false,"items":[]},"players":{"enabled":false,"available":false,"bands":[]}}}}
```

Response (the LLM may set a wood species or request a common resource item):

```json
{"reply":"Tamam, 12 toprağı bulup sandığa bırakacağım.","task":{"type":"collect_item","itemId":"minecraft:dirt","amount":12},"cancel":false}
```

Wood requests use the existing `collect_wood` intent; common resources use `collect_item` with a canonical item ID. Paper checks the ID against its explicit block-to-output capability registry, plus amount 1–64, chest, job ownership and current availability before starting. The complete vanilla item catalog is for recognition and lookup; an item outside the capability registry fails closed. No executable code, target coordinates or permissions are accepted from the brain.

### `POST /api/decide`

Request:

```json
{"observation":{"generation":8,"task":"collect_wood","materialPreference":"any","requested":20,"collected":3,"cargo":3,"deposited":0,"requiredAction":"select_tree","chestBound":true,"position":"<server-local position>","perception":{"version":1,"available":false,"capturedAt":0,"generation":8,"phase":"UNKNOWN","blocks":{"sampledCells":0,"types":[]},"inventory":{"enabled":false,"available":false,"items":[]},"players":{"enabled":false,"available":false,"bands":[]}}},"actions":[{"id":"target_a","description":"Walk to nearby BIRCH_LOG block, distance 5 blocks"},{"id":"target_b","description":"Walk to nearby OAK_LOG block, distance 8 blocks"},{"id":"wait","description":"Wait briefly"}]}
```

Response:

```json
{"id":"target_a","source":"jev","model":"jev-1.14.0","confidence":0.92}
```

The normal `scripts/run.sh` launch selects the TypeSafe alias `jev-preview`. A deliberately supplied process-level `JEV_MODEL` may select `jev-preview`, `jev-latest`, or a versioned `jev-X.Y.Z` model ID of at most 64 characters. The process value takes precedence over `brain/.env`, so an older dotenv setting cannot silently undo the preview launch default. `GET /health` may report the validated configured alias; that is configuration metadata, not evidence of a provider call or resolved version. A JEV choice is accepted only when the response includes a plausible versioned `model` ID of at most 64 characters and a choice among the offered candidates. Missing or invalid metadata falls back safely.

Provider failures are reduced to a provider name and bounded category, with a validated numeric HTTP status where available, before they reach fallback results. Raw provider response bodies, headers, credentials and private request state are not included in errors or decision logs. OpenAI chat keeps its separate GPT-6 Luna endpoint/model and success path.

`source` can also be `heuristic`, `forced`, `fallback`. Server can only execute actions actually offered for the current task generation. Responses from cancelled/replaced jobs are discarded.

### `POST /api/event`

```json
{"type":"task_delivered","actor":"<Minecraft UUID>","count":20}
```

Acknowledge `{ "ok": true }`. Allowed types: `task_started`, `task_cancelled`, `task_delivered`. These events feed bounded conversation memory. Server can lose an event when the brain is offline; core task execution remains authoritative in Paper YAML.

## Bounded grounded perception (M2)

The plugin attaches a generation-stamped v1 snapshot to `/api/chat` context and `/api/decide` observations. It is read-only, partial, advisory data. It cannot create goals, authorize actions, supply coordinates, or change existing task/cargo/recovery priority. Perception makes no additional model calls; the existing offered-action validation and Java authority remain decisive.

- `perception.enabled` defaults to `true` for block sampling. Paper checks the main thread before reading NPC/world state, samples only already-loaded cells within four blocks along four cardinal rays, stops at opaque or unloaded cells, and reads at most 64 cells. It emits at most eight aggregate material counts. A bounded ray or unloaded edge is not full-world coverage.
- `perception.include-bound-chest` and `perception.include-players` default to `false`. Inventory data is available only after explicit opt-in and an online same-world viewer passes `ahmet.admin`; it covers only the already-bound nearby chest, reads the full double-chest view up to 54 slots, and emits at most eight material totals (maximum aggregate 3,456). It reads no player inventory, container names, item metadata, or NBT.
- Player data is available only after opt-in and a valid online same-world viewer context. The adapter considers at most 32 online candidates, excludes the viewer and filters foreign-world, invisible, or hidden players before position reads. It emits at most eight anonymous `near` (within four blocks) or `nearby` (within eight blocks) bands; no name, UUID, world name, or coordinates are included.
- Snapshot JSON contains only `version`, `available`, `capturedAt`, `generation`, `phase`, and the `blocks`, `inventory`, and `players` sections. Node accepts only the v1 field/type allowlist, enforces a 20-second maximum age and one-second future skew, and requires the caller's current generation. Missing, stale, malformed, unsafe-integer, or mismatched input becomes minimal unavailable data; unknown fields are stripped before every provider serialization. Old plugin requests without perception remain usable.
- All Bukkit, Citizens, world, block, player, and inventory access remains on the Paper main thread. HTTP work is asynchronous and bounded. Chat callbacks compare the generation captured before the request with the current job generation before applying any reply, task, cancel, or home effect.

The model may use the sanitized observations as context, but unavailable or partial sections mean “unknown,” not “empty world/chest.” A visible-player band is not an identity and does not grant permission. Runtime evidence and the human/provider boundary are recorded in `docs/VALIDATION_RESULTS.md` and `docs/ACCEPTANCE.md`.

## Bounded conversational memory

The Node brain persists schema v2 JSON and migrates valid v1 conversation history and audit rows on load. Memory is isolated by the exact actor UUID; actor maps use own-property-safe storage. Anonymous or malformed actors are never assigned to another player. The store retains at most 200 actors, 500 combined verified/audit events, 20 conversation turns per actor, and six turns of history per model request.

Each actor profile stores a bounded display name, first/last-seen timestamps, and interaction count. `new` means fewer than two recorded turns, `familiar` means 2–9, and `established` means 10 or more. These are deterministic familiarity labels only; they confer no trust, permissions, or task authority.

A session rolls over after 30 minutes idle. It stores deterministic compact snippets of player discussion and validated typed task requests, not semantic extraction of personal facts. The current summary and at most five prior session summaries are each capped at 1,000 characters. No additional LLM summarization call is made. Player requests and assistant claims stay distinct from server-reported task events. Only `task_started`, `task_cancelled`, and `task_delivered` events with a valid actor enter verified model context; legacy chat audit rows and unknown event types remain separately stored and are never promoted to verified outcomes. Model memory is explicitly labeled untrusted context, is bounded before serialization, and cannot bypass the existing task validator, busy/cargo state, or chest requirement.

### `GET /health`

Unauthenticated localhost readiness probe; returns service name and configured mode, not credentials.

## Task state machine

```text
IDLE -> FIND_TREE -> WALK_TREE -> HARVEST -> [repeat until requested]
                                     -> WALK_CHEST -> DEPOSIT -> DONE
                                      cancellation -> CANCELLED -> DELIVER_RECOVERY
```

Each harvest writes an atomic `operations.json` intent including the exact block, expected output item, and pre-operation per-material cargo count before changing the block, then updates per-item cargo in `config.yml` and commits the journal entry. Task YAML is written to a same-directory temporary file and atomically replaced where supported; write failures lock task execution and propagate, leaving the journal pending. Journal format v3 records deposit inventory size and exact slot changes. Deposit recovery only reconciles task counters when every changed slot matches its recorded after-state; before, partial, or ambiguous slots pause recovery for manual inspection rather than recreating items. Version 1 oak harvest journals remain readable. Version 2 entries lacking the new recovery fields are preserved and fail closed. Startup reconciles pending operations conservatively; a changed task, block, chest, or slot pauses work and preserves the journal for manual inspection. This reduces silent duplication/loss, but atomic replacement is not a claim of power-loss durability, and full forced-crash behavior still needs an in-game Paper restart test.

Chest transactions use `Chest.getInventory()` so a double chest is treated as one 54-slot inventory. `getBlockInventory()` is intentionally not used: Paper documents that it returns only the bound half of a double chest, which can falsely appear full even when the other half is empty. Startup journal recovery uses the same full-inventory view so planned slot indices remain consistent.

If three separate resource routes fail or two consecutive scans find no remaining eligible target after cargo was collected, the task switches to a persisted partial-delivery state. Ahmet tries to deliver the exact cargo and reports the collected/requested amount; it does not silently claim the full request succeeded. A full chest or unreachable chest still retains cargo and requires operator/player recovery.

Wood provenance is conservative and local to this plugin. Main-thread `BlockPlaceEvent` handlers persist coordinates for player-placed logs/stems; eligible wood at those coordinates is never offered to JEV. A configurable spherical home exclusion protects pre-existing structures because vanilla world data has no historical placer metadata. The radius defaults to 10 blocks and operators can set its center/radius with `/ahmet protecthome <0-32>`. The existing leaf/fungus heuristic remains an additional natural-tree signal. Prior edits elsewhere, command edits, and edits made by other plugins cannot be identified retroactively and need an explicit protected area.

## Deterministic Citizens schedule

`ScheduleAdapter` owns routine navigation and bed sleep for the real Citizens `VILLAGER`; Paper remains authoritative and all Citizens/Bukkit calls stay on the server main thread. The schedule uses world time modulo 24,000 ticks: WAKE `[0,1000)`, WORK `[1000,9000)`, WANDER `[9000,12000)`, and SLEEP `[12000,24000)`. WORK means that Ahmet is idle and available at home; it does not launch autonomous harvesting. Scheduling is enabled by default with `options.schedule-enabled: true`.

At WANDER, the planner tries a bounded set of safe, loaded locations no farther than six blocks from the saved home, without changing blocks or loading chunks. If Ahmet is outside that radius, the adapter cancels the wander route once and retains its inward home route under the watchdog and bounded retry policy. It stays in recovery through the intermediate 2.5-to-6-block zone and resumes wandering only after reaching within 2.5 blocks of home. During WAKE and WORK, and at night, Ahmet returns toward home. An operator can bind an existing bed by looking at it within six blocks and running `/ahmet bed`; the plugin persists only its location. It does not create or replace beds. For sleep, Ahmet first walks to a safe standing location adjacent to the validated bed and only then invokes the native villager sleep behavior. Missing, occupied, stale, wrong-world, or unreachable beds fail closed and leave Ahmet resting at home with the current reason exposed by `/ahmet status`.

An active job, cargo, recovery block, or outstanding journal work preempts schedule movement and sleep immediately. Schedule navigation uses Citizens pathfinding with bounded precision for routine waypoints; it does not teleport or alter task-navigation defaults. Only an explicit operator `/ahmet summon` while idle may reposition Ahmet. Schedule retries are bounded and cooled down rather than retried each tick. Runtime probe observations and manual gameplay gaps are recorded in `docs/VALIDATION_RESULTS.md` and `docs/ACCEPTANCE.md`.

## Bounded presentation behavior (M4)

Presentation is a best-effort layer around the authoritative task engine. Paper/Citizens entity, player, world and inventory reads and mutations stay on the server main thread; HTTP work remains asynchronous and time-bounded. A cosmetic API failure must not change task, cargo, chest, journal or recovery outcomes.

- While Ahmet is idle, awake and not navigating, the adapter may look at a nearby eligible player. It examines at most 32 candidates, within six blocks, and reserves scans at least 20 server ticks apart. Chunk preflight checks already-loaded chunks and does not load chunks. Native Bukkit invisibility and spectator state are excluded; third-party vanish plugins are not claimed as supported.
- Nearby public speech is limited to 18 blocks and at most 16 recipients. A private model reply targets only the initiating player and revalidates online state, same-world presence and range when the asynchronous response returns. Public and private lines share a per-player UUID one-second cooldown, so a recent public line can suppress a private reply; delivery is best effort.
- After a successful, journaled harvest or chest deposit, the real Citizens villager may swing its arm and play a short work sound. These effects are cosmetic and best effort. Ordinary `RuntimeException` failures are contained and emit fixed-category diagnostics throttled to one per category every 10 seconds; fatal JVM errors are not swallowed.

The M4 disposable runtime verifies real Citizens VILLAGER movement/effect delegation and task transactions with a mock bridge. The reviewed source/docs were published and the exact core from the corrected four-entry package was installed; a fresh Paper/Node startup, health/configuration, idle status, and save read-back are recorded in `docs/VALIDATION_RESULTS.md`. Startup is not a provider-call or gameplay test. Synthetic Player/LOS proxies do not establish actual client packet delivery, client-visible head/arm animation, human-readable audio, or multiplayer acceptance.

## Opt-in production goal policy (M3)

`AgentController` now selects bounded goals from server-authored facts in the production task loop. This policy advances only an explicitly admitted, authorized job: it may harvest an offered resource, clear an eligible obstruction within its budgets, explore within the existing home boundary, retry an owned route, return safely, or request player help. It does not invent village needs, admit work by itself, or authorize a new job. The option `options.production-goals-enabled` defaults to `false` and must be explicitly enabled by an operator.

Task ownership and generation, cargo, recovery/journal work, pending scans, and schedule state retain priority. A selection is rejected if its task ID, owner, job generation, or extended global server-step identity is stale; navigation cancellation and clearing require the exact owned route/action sequence. During ordinary in-bound work, a foreign Citizens route is preserved. Mandatory safe-home return and ready-cargo delivery have higher priority and may supersede it. The task waits at most 100 server ticks before attempting a help request; the shared 30-second help cooldown can delay acceptance and the visible message. While cooldown is active, the waiter retries on each distinct tick until acknowledged, but starts at most one help sequence. Owned-route retries are exposed only when the actual retry policy says another attempt is available. For active-task route failures, watchdog GIVE_UP becomes terminal, cleans up the failed route, and requests visible help; that request does not block safe cargo delivery. Partial delivery reports its outcome and retains cargo. Productive transitions are capped at 32. When the cap is reached, the policy requires safe return and delivery of existing cargo; it cannot begin further productive work. Obstruction clearing is separately limited to 3 per route and 12 per job. The command/output limit of 64 items is a different bound.

All entity, world, inventory, Bukkit and Citizens access remains on Paper's main thread; bounded HTTP work stays asynchronous. Normal task and schedule movement use Citizens pathfinding and never teleport. Only an explicit operator `/ahmet summon` while idle may reposition the NPC. The narrow Citizens activation compatibility path is enabled only for Paper identity `1.21.6-48-4d854e6` and `Citizens-2.0.41-b4138.jar`; it resolves `net.citizensnpcs.util.NMS.activate(org.bukkit.entity.Entity)` once before entity work, applies only to an eligible owned route, and is bounded once per entity per tick. A missing or incompatible API fails closed. Keep the dependency JAR basename exact; transient Citizens metadata may be created only when absent, and operator settings are preserved. No full-NPC update or direct Paper entity-field write is used.

If the bound bed is in another world, or its world is unloaded, the schedule fails closed to the saved home; it does not load that world or teleport the NPC. Exact final source, runtime and package-candidate evidence is recorded in `docs/VALIDATION_RESULTS.md`. Publication and deployment remain separate release gates.

Goal-policy unit and disposable runtime results are recorded in `docs/VALIDATION_RESULTS.md`. The final four-entry package has been rebuilt locally and verified with ZIP SHA-256 `5781ea3489fef9f5828f0c21cc980a414ed9f8c8602a1057b9ce3adb0788c207`; the local `dist/` directory is gitignored. Source publication and deployment/read-back are complete. Diagnostic field interventions are fixture-only and do not count as normal runtime, provider, human, or crash acceptance; human gameplay, current-path provider behavior and forced-crash acceptance remain open.

## Allowlisted transactional crafting (M5)

Crafting in Villager Ahmet is implemented as an allowlisted, transactional, chest-centric operation executed on the Paper main thread. It supports exactly two vanilla recipes:
- `minecraft:oak_planks`: consumes 1 plain oak log, wood, or stripped oak variant (`OAK_LOG`, `OAK_WOOD`, `STRIPPED_OAK_LOG`, `STRIPPED_OAK_WOOD`) and yields 4 `OAK_PLANKS`.
- `minecraft:stick`: consumes 2 plain planks from the 12-species allowlist (`OAK_PLANKS`, `SPRUCE_PLANKS`, `BIRCH_PLANKS`, `JUNGLE_PLANKS`, `ACACIA_PLANKS`, `DARK_OAK_PLANKS`, `MANGROVE_PLANKS`, `CHERRY_PLANKS`, `BAMBOO_PLANKS`, `CRIMSON_PLANKS`, `WARPED_PLANKS`, `PALE_OAK_PLANKS`) and yields 4 `STICK`.

Crafting quantities are bounded to `4..64` in exact multiples of 4; non-multiples fail closed without rounding. Input items with custom display names, lore, enchantments, or non-default metadata are strictly ignored by `CraftingPlanner` to preserve player heirlooms.

### Shared task lifecycle and admission gates
Crafting shares the authoritative `WoodTask` structure (`Kind.GATHER` vs. `Kind.CRAFT`). Tasks enter either via operator/player command `/ahmet craft <oak_planks|stick> <4-64>` or through the Node brain's `/api/chat` endpoint as `{ "type": "craft_item", "recipe": "...", "amount": N }`. Admission requires an idle actor, a bound chest in the same world, and a pre-flight plan check (`CraftingPlanner.plan`) verifying sufficient inputs and output capacity across the full 54-slot double-chest view (`Chest.getInventory()`).

Once admitted, Ahmet uses Citizens pathfinding to walk to the bound chest without teleporting (`totalDistanceWalked >= 3.0`, `maxStep <= 0.22`, `teleports == 0`). Upon arrival, preconditions and inventory contents are re-validated: if inputs or slots were removed during transit, the task aborts cleanly (`abortCraftTask`), emitting `task_cancelled` without modifying any items or creating fake deliveries.

### Journal v4 and crash recovery
Before any chest inventory mutation occurs, the operation writes a write-ahead journal record (`OperationJournal.Craft`) into `operations.json` (journal format v4). The entry captures before/after inventory snapshots, requested counts, slot changes, and owner metadata. Inventory changes are applied atomically, task completion counters are updated, and the journal entry is retired only after a clean world save.

On startup, `OperationRecovery.reconcilePendingCraft` inspects the saved chest state and task counters:
- `APPLY_REMAINING`: the chest remains at the pre-operation state; mutations are executed, task is credited, and the journal is retired.
- `COMPLETE_ALREADY_APPLIED`: the chest already contains the crafted output; the task is marked completed without double-crediting items.
- `FINALIZE_ALREADY_COMPLETED`: both chest and task reflect completion; the journal is retired immediately.
- `CONFLICT`: chest contents or task parameters do not match before or after states; the plugin halts into `recoveryBlocked = true` and preserves `pendingCraft` for operator inspection.

When a `pendingOperation` or `recoveryBlocked` is active, `canCancelOrReturnHome()` prevents `/ahmet cancel` or idle home-return until recovery is reconciled.

## Gameplay assumptions

- Version pin: Minecraft Java 1.21.6 / Paper 1.21.6 / Java 21 / Citizens 2.0.41 build 4138 (1.21_R5 adapter).
- A bound chest must remain in the same world. Task is paused with a warning when missing.
- Wood candidates are blocks in Bukkit's `Tag.LOGS` plus natural bamboo stalks, with nearby leaf/fungus or bamboo-stalk markers. Common resources map dirt/grass to dirt, sand/red sand to themselves, stone to cobblestone, and coal/iron/gold ore to coal/raw ore. Ore mappings use the minimum wooden/stone/iron pickaxe as appropriate and require exactly one matching Paper-calculated drop; variable drops and Silk Touch/Fortune behavior are not requested. Default search radius is 24 blocks and only already loaded chunks are inspected. A main-thread cursor visits at most 512 coordinates per tick; incomplete scans remain pending rather than being interpreted as empty. The cursor is invalidated by task generation, world, or preference changes. Java revalidates candidates before acting, bounds checks to 64 candidates, and offers at most eight safe target IDs. JEV chooses among those IDs; it never supplies coordinates. Travel, harvest and deposit then run as deterministic server actions without a model call per movement tick.
- Citizens navigation must be observed in-world. A stalled route is cancelled and reissued after two seconds; after three failed attempts, that block is temporarily excluded for one minute so JEV can choose another candidate. Pathfinding cannot be validated with Node unit tests.
- The only NPC teleport is operator-requested `/ahmet summon` while idle; normal task navigation uses Citizens pathfinding without teleporting. Safe-ground checks reject magma, cactus, fire, soul fire, campfires, berry bushes, wither roses, and powder snow in floor/feet/head cells.
- No player inventory or gameplay resource is silently duplicated; server consumes a block before crediting the single verified virtual cargo item. This is still not vanilla mining physics, and tool durability is not simulated.
- Trust model is friendly private server, not hostile users. For public deployment, require permissions, block-claim region policies and anti-grief controls.
