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

Each harvest writes an atomic `operations.json` intent including the exact block and expected output item before changing the block, then updates per-item cargo in `config.yml` and commits the journal entry. Deposits plan exact slots for one output item at a time, write that item and slot plan before changing inventory, persist task state, flush the world, and only then clear the journal. Version 1 oak journals remain readable as oak. Startup replays pending operations idempotently; a changed task, block, chest, or slot pauses work and preserves the journal for manual inspection. This reduces silent duplication/loss, but full forced-crash behavior still needs an in-game Paper restart test.

## Gameplay assumptions

- Version pin: Minecraft Java 1.21.6 / Paper 1.21.6 / Java 21 / Citizens 2.0.41 build 4138 (1.21_R5 adapter).
- A bound chest must remain in the same world. Task is paused with a warning when missing.
- Wood candidates are blocks in Bukkit's `Tag.LOGS` plus natural bamboo stalks, with nearby leaf/fungus or bamboo-stalk markers. Common resources map dirt/grass to dirt, sand/red sand to themselves, stone to cobblestone, and coal/iron/gold ore to coal/raw ore. Ore mappings use the minimum wooden/stone/iron pickaxe as appropriate and require exactly one matching Paper-calculated drop; variable drops and Silk Touch/Fortune behavior are not requested. Default search radius is 24 blocks and only already loaded chunks are inspected. Java bounds checks to the nearest 64 matching blocks and offers at most eight safe target IDs. JEV chooses among those IDs; it never supplies coordinates. Travel, harvest and deposit then run as deterministic server actions without a model call per movement tick.
- Citizens navigation must be observed in-world. A stalled route is cancelled and reissued after two seconds; after three failed attempts, that block is temporarily excluded for one minute so JEV can choose another candidate. Pathfinding cannot be validated with Node unit tests.
- The only NPC teleport is operator-requested `/ahmet summon` while idle; normal task navigation uses Citizens pathfinding without teleporting.
- No player inventory or gameplay resource is silently duplicated; server consumes a block before crediting the single verified virtual cargo item. This is still not vanilla mining physics, and tool durability is not simulated.
- Trust model is friendly private server, not hostile users. For public deployment, require permissions, block-claim region policies and anti-grief controls.
