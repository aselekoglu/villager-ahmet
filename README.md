# Villager Ahmet

An AI-assisted **real villager NPC** for a private Minecraft Java Edition multiplayer world. Not a player bot wearing a villager skin.

**Status: Paper/Citizens build verified; gameplay is not yet verified.** The plugin compiles for Paper 1.21.6 with Citizens 2.0.41 build 4138, and the Node and Java unit suites pass. The EULA has now been accepted by the user; an in-game run and live multiplayer checks are still pending (see `docs/VALIDATION_RESULTS.md`).

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
        ├── actual log removal (server-side world edit)
        ├── virtual cargo, persisted in plugin YAML
        └── actual chest insertion
                     │ local authenticated HTTP
      Node.js brain (127.0.0.1:3197)
        ├── Turkish/English bounded task parser
        ├── chat LLM (OpenAI chat/completions, GPT-6 Luna)
        ├── JEV (TypeSafe System One, account alias `jev-latest`)
        ├── JSON memory persisted to disk
        └── deterministic mock/fallback mode
```

The code is original and does not incorporate files from `rmalde/minecraft-agent`; that repo does not currently show a license. The system borrows the **concept** of high-level planning + JEV typed action selection. Minecraft entity control is implemented with Citizens rather than Mineflayer.

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

From the Minecraft server console, add every real Java username and make yourself operator:

```text
whitelist add YourUsername
op YourUsername
```

Join Minecraft Java **1.21.6** with multiplayer server `localhost:25565` on the server computer, or its LAN IP on another computer. Remote play requires **secure networking/VPN or firewall-managed port access**; do not put the local brain API or Paper admin interfaces on the Internet. EULA and Minecraft server/client licensing remain yours to respect.

## In-game walkthrough

1. In the server as operator, stand where Ahmet should live, enter `/ahmet spawn`. If Ahmet already exists and is idle, `/ahmet summon` moves him to a safe nearby block.
2. Put a chest beside your house, look directly at it from within six blocks, enter `/ahmet chest`.
3. Ensure naturally generated *oak trees* are within ~16 blocks of Ahmet (configurable). For testing, place Ahmet, a chest and a naturally leaf-connected oak tree in safe, level terrain.
4. Stand within 18 blocks of Ahmet and type `Ahmet, 20 tane meşe odunu topla`, or `/ahmet ask 20 meşe odunu topla`.
5. Watch Ahmet move toward real logs, remove them one at a time, and then walk to deposit the exact collected number into the configured real chest. Verify with `/ahmet status` and chest inventory.

Other commands: `/ahmet cancel` (task owner or op), `/ahmet deliver` (recover cargo after cancellation), `/ahmet ask <mesaj>` (speech), `/ahmet status`. Only operators can spawn/summon Ahmet or bind the chest. Summoning is disabled during an active task or journal recovery.

**MVP realism caveat:** Mining is performed by the *Paper server plugin* removing a physically present oak log and crediting one corresponding virtual cargo item; it is **not** native villager item pickup or a Mineflayer dig packet. Ahmet visibly walks, changes the real world, and deposits real items. Animation, actual carried-item render, protected-region hooks, proper tree detection, advanced navigation and autonomous routines are next milestones.

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
- At most 64 oak logs per job. No LLM/JEV teleport, inventory creation, cheats, shell execution, arbitrary game commands or block placement in the action contract. Only an explicit operator `/ahmet summon` can reposition the NPC, and never during task navigation.
- Server plugin exclusively authorizes block deletion and chest deposit, on the Paper main thread. Async model calls never touch Bukkit world state.
- Task state persists in `plugins/VillagerAhmet/config.yml`; `plugins/VillagerAhmet/operations.json` journals harvest and chest-slot changes for restart reconciliation; conversational events persist separately in `brain/data/memory.json`. Unit tests cover replay and conflict detection, but a forced-crash Paper-world test is still required before using valuable items. Conflicting world/chest state pauses recovery for manual inspection.
- Keep Paper and Citizens versions pinned and tested together; never automatically update Minecraft on a running persistent world.

## Main design documents

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — trust boundaries and protocol
- [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md) — actual success/failure criteria
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — post-MVP growth
- [`CODEX_HANDOFF.md`](CODEX_HANDOFF.md) — one-shot Codex implementation/verification instructions
