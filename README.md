# Villager Ahmet

An AI-assisted **real villager NPC** for a private Minecraft Java Edition multiplayer world. Not a player bot wearing a villager skin.

**Status:** common-resource gathering, the Paper 1.21.6 item catalog, cargo/recovery journaling, double-chest delivery, protected-home filtering, bounded Citizens navigation, and a deterministic Citizens day schedule are implemented. Latest Java source verification: **155 tests passed**; the existing Node suite has **16 passing tests**, and mock HTTP smoke passes. Current schedule JAR `4041f4bb…` passed fresh and same-world restart probes, plus all seven common-resource groups in a disposable Paper server. The same JAR is installed on the private server; Paper startup and `save-all` completed, though `/ahmet status` reported the saved NPC as unspawned (`NPC: yok`). Live player schedule behavior and live provider calls remain unverified. See [`docs/VALIDATION_RESULTS.md`](docs/VALIDATION_RESULTS.md) and [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md). Ahmet is a real Citizens villager.

## Architecture

```text
Minecraft Java clients (same game version, unmodified)
                     │
      Private Paper 1.21.6 server (Java 21)
                     │
       Citizens villager NPC "Ahmet"
                     │
        VillagerAhmet Paper plugin
        ├── in-world NPC navigation
        ├── bounded common-resource block removal with exact drop checks
        ├── per-material virtual cargo, persisted in plugin YAML
        └── actual chest insertion
                     │ local authenticated HTTP
      Node.js brain (127.0.0.1:3197)
        ├── chat/task intent (OpenAI chat/completions, GPT-6 Luna JSON)
        ├── JEV (TypeSafe System One, account alias `jev-latest`)
        ├── JSON memory persisted to disk
        └── deterministic mock/fallback mode
```

The code is original and does not incorporate files from `rmalde/minecraft-agent`; that repo does not currently show a license. The system borrows the **concept** of high-level planning + JEV typed action selection. Minecraft entity control is implemented with Citizens rather than Mineflayer. Resource discovery advances incrementally on the Paper main thread, with a bounded coordinate budget per tick; a scan is not treated as empty until its cursor completes.

## Setup (macOS or Linux)

Prerequisites: Minecraft Java Edition 1.21.6 accounts/clients; **Java 21**, **Node.js 20+**, **Python 3**, **Maven 3.9+**, a few GB of RAM, outbound Internet for downloading Paper/Citizens/build dependencies and model calls. Test with a *new* private world, not an irreplaceable save. Both clients connect to the Paper server; they don't need client mods. Live JEV and chat use separate credentials: `TYPESAFE_API_KEY` for TypeSafe and `OPENAI_API_KEY` for OpenAI GPT-6 Luna. Missing keys produce clearly labelled heuristic/mock behavior.

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/opt/maven/bin:$PATH"
./scripts/doctor.sh
./scripts/setup.sh
# Set TYPESAFE_API_KEY and OPENAI_API_KEY in the ignored brain/.env for live calls.
# If you have read and agree to the EULA:
ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
```

This intentionally does **not** auto-consent to Mojang's EULA. The setup script downloads a checksummed stable Paper 1.21.6 build from the official PaperMC API and pinned Citizens 2.0.41 build 4138, creates a random local bridge token and compiles the plugin against the matching Citizens API. You can also download those exact official JARs yourself into `server/` and `server/plugins/` if the download endpoints fail.

To preserve the default test server while hosting a copied world separately, set `AHMET_SERVER_DIR=server-live` for both setup and run. Relative paths are resolved under the project root; the source world should be copied into that directory's `world/` only after the server has been set up and stopped.

From the Minecraft server console, add every real Java username and make yourself operator:

```text
whitelist add YourUsername
op YourUsername
```

Join Minecraft Java **1.21.6** with multiplayer server `localhost:25565` on the server computer, or the server computer's LAN IP (currently `10.0.0.140:25565`) on another computer on the same network. Per the user's request, this private LAN server uses `online-mode=false` and keeps the whitelist enabled so the offline TLauncher profile can join. Offline mode lets clients on that LAN claim usernames and changes UUID-based player identity; use only on the trusted LAN, do not configure port forwarding, and restore `online-mode=true` before any public/WAN exposure. Do not put the local brain API or Paper admin interfaces on the Internet.

## In-game walkthrough

1. In the server as operator, stand where Ahmet should live, enter `/ahmet spawn`. If Ahmet already exists and is idle, `/ahmet summon` moves him to a safe nearby block.
2. Set the schedule bed by looking at an existing bed from within six blocks and entering `/ahmet bed`. This stores only the bed location; it does not create or replace a bed. Put a chest beside your house, look directly at it from within six blocks, enter `/ahmet chest`.
3. Ensure safe targets are within 24 blocks of Ahmet in already loaded chunks (configurable): natural tree logs/stems and bamboo, dirt/grass blocks, sand/red sand, stone (cobblestone output), coal ore, iron ore, or gold ore. Planks and crafted bamboo blocks are not searched. Ore harvesting checks Paper's block drops with the minimum pickaxe tier; fortune/silk-touch variants and variable-output drops are excluded.
4. Stand within 18 blocks of Ahmet and type `Ahmet, 20 tane odun topla`, `12 toprak topla`, `8 kum getir`, `10 taş topla`, `4 kömür topla`, `5 ham demir getir` or `3 ham altın getir`.
5. Watch Ahmet walk toward a server-discovered candidate, remove one supported block at a time, and then walk to deposit the exact matching output items into the configured real chest. Verify with `/ahmet status` and chest inventory.

Commands: `/ahmet help` lists commands; `/ahmet status` shows task/NPC state and schedule phase/activity/reason; `/ahmet bed` binds an existing nearby bed for the schedule; `/ahmet cargo` shows exact carried item types and counts; `/ahmet items <ara>` searches the full catalog and reports gatherability; `/ahmet ask <mesaj>` sends a direct chat request. `/ahmet collect <item|odun> <1-64>` starts a bounded collection job directly, without chat-model parsing, while still using the same Paper validation and JEV target selection (examples: `/ahmet collect odun 8`, `/ahmet collect raw_iron 5`, `/ahmet collect birch_log 4`). `/ahmet cancel` is limited to the task owner or operator; `/ahmet deliver` sends leftover cargo to the bound chest. Only operators can spawn/summon Ahmet, bind the bed, or bind the chest. Summoning is disabled during an active task or journal recovery.

If a task is cancelled after Ahmet has collected items, run `/ahmet deliver` to clear the carried cargo into the bound chest before starting another task.

If Ahmet cannot reach three different resource targets, or two consecutive scans find no remaining nearby target, he automatically stops that request and tries to deliver the cargo already collected. The reply identifies it as a partial result and includes the collected/requested amount. A blocked or full delivery chest still requires the path to be cleared or space to be made; cargo remains saved in that case.

Wood protection distinguishes new player edits from natural targets by recording player-placed log/stem blocks and skipping them permanently. Minecraft does not retain placement history for blocks that existed before this plugin, so an existing build needs a protected area: stand at its center and run `/ahmet protecthome 10` (choose a radius from 0–32; `0` disables the area). Ahmet also requires natural leaves/fungus markers for untracked wood. The tracked placement registry and protected-home center survive restart. Commands or other plugins that change blocks do not fire normal player-place events; protect those areas manually.

The plugin also exports Paper 1.21.6's complete obtainable vanilla item-material catalog to `plugins/VillagerAhmet/item-catalog-1.21.6.json`. In game, `/ahmet items <ad>` searches that catalog. Catalog presence means Ahmet can identify and validate the vanilla item ID; it does **not** mean every item has a gather action. The executable collection registry currently supports logs/stems and natural bamboo, dirt, sand/red sand, stone-to-cobblestone, coal ore, iron ore and gold ore; unsupported items are listed as not yet gatherable.

**MVP realism caveat:** Harvesting is performed by the *Paper server plugin* removing an eligible physical block and crediting the exact checked output to virtual cargo; it is **not** native villager mining or item pickup. Ahmet visibly walks, changes the real world, and deposits real items. Tool durability is not consumed, and the plugin's natural-tree marker is a heuristic. The deterministic schedule is a scripted routine: WAKE and WORK stay at home, WANDER walks within six blocks of home, and SLEEP routes to the operator-bound bed; WORK does not start harvesting. Tasks, carried cargo, or recovery work preempt schedule movement and sleep. Schedule runtime evidence, including failed probe iterations and any remaining manual acceptance, is recorded in the validation documents. Animation, actual carried-item render, and protected-region hooks remain follow-ups; saved registry files were not edited.

## Model modes

- `AHMET_DECISION_MODE=jev` + valid `TYPESAFE_API_KEY`: use `POST https://api.typesafe.ai/v1/systemone` with the account-listed `JEV_MODEL` (currently `jev-latest`). The JEV response is only a choice among *server-offered legal actions*; it never receives authority to run commands.
- `AHMET_CHAT_MODE=llm` + valid `OPENAI_API_KEY`: use `POST https://api.openai.com/v1/chat/completions` with `CHAT_MODEL` (defaults to `gpt-6-luna`).
- No API key / `mock` modes: deterministic proof-of-concept. **Do not claim a JEV live integration test was performed in mock mode.**
- If an API decision fails, the brain returns a labelled fallback so existing tasks can continue. Do not silently pass off fallback as JEV.

Store each provider key in `brain/.env` (ignored by Git), never in the plugin, logs or messages. The plugin talks to the locally bound brain with a separate, randomly generated `BRIDGE_TOKEN`. Neither Paper nor the clients need either model key.

## Tests

```bash
cd brain && npm test
cd .. && ./scripts/smoke.sh
cd paper-plugin && mvn -B -ntp package
```

Then perform the **manual multiplayer acceptance scenario** in `docs/ACCEPTANCE.md`. The repository CI workflow runs the Node suite, mock HTTP smoke test, Maven package build, and Java tests. A hosted GitHub Actions result still requires this workspace to be connected to a GitHub repository.

## Boundaries and operational safety

- Private/trusted worlds only. A simple oak-leaves adjacency heuristic prevents some structure damage but cannot guarantee that every oak log belongs to a tree; don't deploy on a public/protected world without permission checks and stricter tree detection.
- The NPC is a *Citizens-managed villager* (not a vanilla villager with naturally modified brain). Commands, movement and inventory changes are server controlled.
- At most 64 output items per job. GPT-6 Luna may classify a bounded wood preference or canonical item ID; Paper accepts only the explicit common-resource registry. JEV may choose only among loaded, nearby, Java-validated candidate blocks. JEV runs at target selection, while Paper deterministically handles navigation and item transactions. Neither model can supply coordinates, teleport, create inventory, run commands, cheat, or place blocks. Only an explicit operator `/ahmet summon` can reposition the NPC, and never during task navigation.
- Server plugin exclusively authorizes block deletion and chest deposit, on the Paper main thread. Async model calls never touch Bukkit world state.
- Task state persists through a same-directory temporary YAML file and replacement; failures propagate to the transaction and prevent journal completion. `plugins/VillagerAhmet/operations.json` uses journal format v3 for harvest and chest-slot changes; legacy v1 oak harvests remain readable, while older entries without recovery fields fail closed for manual inspection. Deposit recovery checks the recorded inventory shape and requires every changed slot to match the recorded after-state before reconciling task counters. Unit tests do not establish power-loss durability or world-level crash safety; a forced-crash Paper-world test remains required. Conflicting world/chest state pauses recovery for manual inspection.
- Keep Paper and Citizens versions pinned and tested together; never automatically update Minecraft on a running persistent world.

## Main design documents

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — trust boundaries and protocol
- [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md) — actual success/failure criteria
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — post-MVP growth
- [`CODEX_HANDOFF.md`](CODEX_HANDOFF.md) — one-shot Codex implementation/verification instructions
