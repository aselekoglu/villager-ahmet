# Villager Ahmet

**A villager with a little more on his mind.**

Villager Ahmet is an experimental AI-assisted companion for a private **Minecraft Java Edition** world. He looks and moves like an actual villager, remembers a little about the people he meets, responds to requests, and can help gather everyday resources.

Ask Ahmet for wood in chat, and he can search for a safe nearby tree, walk to it, harvest supported blocks, and bring the resulting items back to a chest. Underneath that simple interaction is a deliberately constrained AI agent: models help interpret requests and choose actions, while the Minecraft server remains responsible for what is actually allowed to happen.

> **The important distinction:** Ahmet is a real **Citizens-managed villager NPC**, not a player bot in a villager skin—and not a vanilla villager whose built-in AI has magically been replaced.

## Meet Ahmet

Imagine sharing a little Minecraft village with a helpful neighbor:

1. You say, **“Ahmet, 20 tane odun topla”** (“Ahmet, collect 20 wood”).
2. Ahmet interprets the request and looks for eligible, nearby resources.
3. He walks to a server-approved target and gathers what he can.
4. He returns to the chest you assigned and deposits the actual items.
5. If he gets stuck, runs out of eligible resources, or cannot deliver, he reports a partial result rather than pretending he succeeded.

You can also interact through commands, so the basic resource-gathering loop works without a live language-model API.

## What works today

| Capability | What it means in-game |
| --- | --- |
| **Real villager presence** | A Citizens NPC with in-world navigation, a configurable home, a bed, and a small daily routine. |
| **Natural-language requests** | Chat can turn bounded requests into supported gathering tasks. |
| **Resource gathering** | Logs/stems, natural bamboo, dirt, sand, stone, coal, iron, and gold are supported through specific validated block/drop mappings. |
| **Chest delivery** | Gathered outputs are credited to persistent cargo, then inserted into a real assigned chest (including double chests). |
| **Basic memory** | Ahmet keeps limited, player-isolated summaries of recent interactions and sessions. |
| **Grounded awareness** | A bounded snapshot of nearby loaded blocks can inform the agent; additional inventory and anonymous player-distance observations are opt-in and restricted. |
| **Protective boundaries** | Home protection, player-placement tracking, action validation, task ownership, and recovery bookkeeping reduce accidental damage. |
| **Offline-friendly development** | Deterministic mock/fallback modes let you explore and test the system without model credentials. |

**This is a working prototype, not a fully autonomous villager.** The collection loop is implemented with server-side block removal and virtual cargo accounting—not the vanilla mining animation, physical tool use, or item pickup. Ahmet's daily routine is scripted; he does not independently decide to start mining or crafting. Some manual multiplayer, persistence, and live-provider acceptance checks remain open.

See [validation results](docs/VALIDATION_RESULTS.md) for what has actually been tested and [the roadmap](docs/ROADMAP.md) for what is still planned.

## How it works

```text
Minecraft Java players (no client mods)
                 |
      Paper 1.21.6 server
                 |
      Citizens villager: Ahmet
                 |
      VillagerAhmet Java plugin
      - NPC movement and tasks
      - safe target discovery
      - block/drop validation
      - cargo and chest transactions
      - bounded world observations
                 |
      authenticated local HTTP bridge
                 |
          Node.js brain
      - request interpretation (OpenAI)
      - offered-action selection (TypeSafe JEV)
      - player/session context
      - deterministic fallback
```

**The model suggests; the server decides.** The Paper plugin discovers candidate blocks, checks preconditions and drop types, controls navigation, and applies world/inventory changes on the server thread. The Node brain handles conversation and decisions but cannot invent coordinates, execute arbitrary commands, or grant itself new permissions. JEV selects from actions the server has already offered.

The chat and action-selection models are separate integrations; neither is required for deterministic local tests. See [architecture and trust boundaries](docs/ARCHITECTURE.md).

## Quick start

This project targets **macOS or Linux** and a **private, trusted world**. Start with a disposable world—not your only copy of a treasured save.

**You'll need:** Minecraft Java Edition **1.21.6**, **Java 21**, **Node.js 20+**, **Python 3**, **Maven 3.9+**, and Internet access for downloading server dependencies. Minecraft clients do not require mods.

```bash
git clone https://github.com/aselekoglu/villager-ahmet.git
cd villager-ahmet

# Make sure Java 21 and Maven are on your PATH.
./scripts/doctor.sh
./scripts/setup.sh

# Run only after reviewing and accepting the Minecraft EULA.
ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
```

Setup downloads the pinned Paper/Citizens dependencies, compiles the plugin, and configures a random local bridge token. EULA acceptance is intentionally **not automatic**.

For optional live AI behavior, configure your own credentials in the Git-ignored `brain/.env`:

```dotenv
TYPESAFE_API_KEY=your_typesafe_key
OPENAI_API_KEY=your_openai_key
```

Use `TYPESAFE_API_KEY` for the JEV decision integration and `OPENAI_API_KEY` for chat. Without keys, the relevant paths use explicitly labelled mock/heuristic behavior rather than claiming live model responses.

Join the Paper server from Minecraft Java 1.21.6 using `localhost:25565` on the host or its private LAN address from another machine. From the server console, whitelist the participating Java accounts and grant your own account operator privileges:

```text
whitelist add YourUsername
op YourUsername
```

**Network caution:** Keep the whitelist enabled and the server private. If you choose `online-mode=false` for trusted LAN clients, usernames can be spoofed; never expose that setup to the Internet. Do not publicly expose the Node brain or its admin bridge.

For a separate server directory or imported world, use the `AHMET_SERVER_DIR` workflow described in [the setup notes](docs/ARCHITECTURE.md), and back up worlds before changing server versions.

## Your first task

Once connected as an operator:

1. Stand where Ahmet should live and run `/ahmet spawn`.
2. Look at an existing bed within six blocks and run `/ahmet bed` to associate it with his routine.
3. Look at a chest within six blocks and run `/ahmet chest` to set the delivery destination.
4. Make sure supported natural resources are nearby, in loaded chunks.
5. Stand within chat range (18 blocks by default) and try **“Ahmet, 20 tane odun topla”**.

Or skip language-model interpretation entirely:

```text
/ahmet collect odun 8
/ahmet collect raw_iron 5
/ahmet status
/ahmet cargo
```

Useful controls include `/ahmet help`, `/ahmet ask <message>`, `/ahmet items <search>`, `/ahmet cancel`, `/ahmet deliver`, `/ahmet summon`, and `/ahmet protecthome 10`.

Ahmet can identify more Minecraft items than he can collect. The full searchable vanilla item catalog is **not** a promise that every item is gatherable. Jobs are deliberately bounded to at most **64 output items**, and only supported source/output mappings can be executed.

### Protect your builds

Before asking Ahmet to gather wood near a house, use `/ahmet protecthome 10` from its center (adjust the radius as needed). The plugin tracks logs placed by players after it starts recording placements, but it cannot reconstruct the history of existing builds. Tree detection is still heuristic, so protected zones and a test world matter.

If delivery fails because the chest is full or inaccessible, Ahmet retains cargo for later recovery rather than silently discarding it.

## AI, memory, and privacy

Ahmet is designed to have **limited context, not unlimited access**.

- Player history is scoped to each player's identity, uses short deterministic session summaries, and is capped in size and retention. It does not infer private personal facts or confer authority.
- Perception covers a small, loaded area and is read-only. Chest details are disabled by default and require an eligible operator; observed nearby players are represented as anonymous distance bands.
- Snapshots are sanitized and age-checked before model calls. An outdated AI response must not overwrite a newer task.
- API keys stay in `brain/.env`, not in the Minecraft plugin or repository. The server-to-brain bridge uses its own local authentication token.

Models and server-side safety checks are complementary, **not** interchangeable. This code is intended for private experimentation, not deployment on an untrusted public server.

## Testing and project status

```bash
cd brain && npm test
cd .. && ./scripts/smoke.sh
cd paper-plugin && mvn -B -ntp package
```

The repository's recorded M2 verification passed **38 Node tests**, **175 Java tests**, the mock HTTP smoke test, and a disposable Paper/Citizens runtime perception probe. It also records a successful deployment/startup read-back. These results **do not** establish complete real-player gameplay, production-world crash durability, or successful live model calls.

Review the [acceptance checklist](docs/ACCEPTANCE.md) and [detailed validation evidence](docs/VALIDATION_RESULTS.md) before relying on Ahmet in an important world.

## What's next?

The longer-term goal is a villager who feels like part of the world—not merely a chatbot attached to a Minecraft entity. Planned work includes more convincing gestures and sounds, richer autonomous routines, crafting and farming, and eventually cooperation between multiple villagers. These are **future directions**, not current capabilities.

Follow progress in the [roadmap](docs/ROADMAP.md).

## Project documentation

- [Architecture](docs/ARCHITECTURE.md) — components, protocol, trust boundaries, and persistence
- [Acceptance checklist](docs/ACCEPTANCE.md) — gameplay and failure scenarios to verify
- [Validation results](docs/VALIDATION_RESULTS.md) — tested builds, evidence, and remaining gaps
- [Roadmap](docs/ROADMAP.md) — implemented milestones and upcoming ideas
- [Codex handoff](CODEX_HANDOFF.md) — development and verification context

## Inspiration and attribution

Villager Ahmet was inspired by the idea of combining AI planning with a persistent Minecraft-world agent, including the high-level approach explored by [rmalde/minecraft-agent](https://github.com/rmalde/minecraft-agent). This repository uses its **own implementation**, built around Paper, Citizens, and a separate Node brain; it does not copy that project's source files.

---

*Ahmet is still learning how to be a good neighbor. Please test him somewhere he can't accidentally remodel your house.*
