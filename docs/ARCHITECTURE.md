# Architecture and integration contract

## Source-of-truth separation

- **Paper plugin** is authoritative for villager position, chest binding, task execution, cargo and items placed in the chest. All mutable Bukkit/Citizens calls execute on the server main thread.
- **Node brain** is authoritative for chat history, event history, direct TypeSafe JEV calls, direct OpenAI chat calls and per-boundary decisions. It cannot directly invoke Minecraft commands.
- **JEV** is a *typed choice selector*, not the chat model, controller, plugin or pathfinder.
- **Chat model** can respond naturally but has no direct executable tools in this MVP. Tasks enter the engine via the separate validated parser only.
- **Item catalog** is generated at Paper startup from the pinned 1.21.6 `Material` registry and written to `plugins/VillagerAhmet/item-catalog-1.21.6.json`. `/ahmet items <name>` searches the same catalog. Each entry marks its executable capability separately: the full registry is recognition/validation data, not a grant to gather every item.

## Local REST protocol

All POSTs include `Authorization: Bearer <BRIDGE_TOKEN>` and JSON. Brain binds to `127.0.0.1:3197` by default. Every request is bounded. The Java plugin is client, Node brain is server.

### `POST /api/chat`

Request:

```json
{"actorUuid":"<Minecraft UUID>","actorName":"Ata","text":"Ahmet, 20 meşe odunu topla","context":{"busy":false,"chestBound":true}}
```

Response (the LLM may set a wood species or request a common resource item):

```json
{"reply":"Tamam, 12 toprağı bulup sandığa bırakacağım.","task":{"type":"collect_item","itemId":"minecraft:dirt","amount":12},"cancel":false}
```

Wood requests use the existing `collect_wood` intent; common resources use `collect_item` with a canonical item ID. Paper checks the ID against its explicit block-to-output capability registry, plus amount 1–64, chest, job ownership and current availability before starting. The complete vanilla item catalog is for recognition and lookup; an item outside the capability registry fails closed. No executable code, target coordinates or permissions are accepted from the brain.

### `POST /api/decide`

Request:

```json
{"observation":{"task":"collect_wood","materialPreference":"any","requested":20,"collected":3,"cargo":3,"deposited":0,"requiredAction":"select_tree","chestBound":true,"position":"10,64,9"},"actions":[{"id":"target_a","description":"Walk to nearby BIRCH_LOG block, distance 5 blocks"},{"id":"target_b","description":"Walk to nearby OAK_LOG block, distance 8 blocks"},{"id":"wait","description":"Wait briefly"}]}
```

Response:

```json
{"id":"walk_tree","source":"jev","model":"jev-latest","confidence":0.92}
```

`source` can also be `heuristic`, `forced`, `fallback`. Server can only execute actions actually offered for the current task generation. Responses from cancelled/replaced jobs are discarded.

### `POST /api/event`

```json
{"type":"task_delivered","actor":"<Minecraft UUID>","count":20}
```

Acknowledge `{ "ok": true }`. Allowed types: `task_started`, `task_cancelled`, `task_delivered`. These events feed bounded conversation memory. Server can lose an event when the brain is offline; core task execution remains authoritative in Paper YAML.

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

## Gameplay assumptions

- Version pin: Minecraft Java 1.21.6 / Paper 1.21.6 / Java 21 / Citizens 2.0.41 build 4138 (1.21_R5 adapter).
- A bound chest must remain in the same world. Task is paused with a warning when missing.
- Wood candidates are blocks in Bukkit's `Tag.LOGS` plus natural bamboo stalks, with nearby leaf/fungus or bamboo-stalk markers. Common resources map dirt/grass to dirt, sand/red sand to themselves, stone to cobblestone, and coal/iron/gold ore to coal/raw ore. Ore mappings use the minimum wooden/stone/iron pickaxe as appropriate and require exactly one matching Paper-calculated drop; variable drops and Silk Touch/Fortune behavior are not requested. Default search radius is 24 blocks and only already loaded chunks are inspected. A main-thread cursor visits at most 512 coordinates per tick; incomplete scans remain pending rather than being interpreted as empty. The cursor is invalidated by task generation, world, or preference changes. Java revalidates candidates before acting, bounds checks to 64 candidates, and offers at most eight safe target IDs. JEV chooses among those IDs; it never supplies coordinates. Travel, harvest and deposit then run as deterministic server actions without a model call per movement tick.
- Citizens navigation must be observed in-world. A stalled route is cancelled and reissued after two seconds; after three failed attempts, that block is temporarily excluded for one minute so JEV can choose another candidate. Pathfinding cannot be validated with Node unit tests.
- The only NPC teleport is operator-requested `/ahmet summon` while idle; normal task navigation uses Citizens pathfinding without teleporting. Safe-ground checks reject magma, cactus, fire, soul fire, campfires, berry bushes, wither roses, and powder snow in floor/feet/head cells.
- No player inventory or gameplay resource is silently duplicated; server consumes a block before crediting the single verified virtual cargo item. This is still not vanilla mining physics, and tool durability is not simulated.
- Trust model is friendly private server, not hostile users. For public deployment, require permissions, block-claim region policies and anti-grief controls.
