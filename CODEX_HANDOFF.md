# Codex run instruction — Villager Ahmet

You are continuing an existing repository, NOT starting from a product idea. Inspect all source and docs before altering it. Target a **reproducible, usable MVP** for two players on a private Minecraft Java 1.21.6 server with a REAL Citizens villager NPC called Ahmet.

## Primary objective

Make the repository runnable end to end, then validate it in the strongest available environment. Success: admin spawns a real villager Ahmet, admin binds a chest, player says in Turkish 'Ahmet, 20 tane meşe odunu topla', NPC walks to natural oak trees, removes exactly 20 actual oak log blocks, walks to the chest, deposits exactly 20 real oak log items, announces completion; second player can interact. Ahmet is NOT a disguised player bot. Real JEV is a bounded decisions model, LLM is separate for conversation.

## Existing work, verify before changing

- `brain/src`: Node.js HTTP brain with bounded Turkish/English parsing, direct TypeSafe JEV choice, direct OpenAI GPT-6 Luna conversation, persistent event/chat memory, token auth, mock fallback.
- `paper-plugin/src`: Java 21 Paper + Citizens plugin creating EntityType.VILLAGER, commands, chat listener, Citizens navigator, oak log removal with virtual cargo, deposit to bound chest, persistent YAML state.
- `scripts`: setup (Paper API download, Citizens CI download, Maven compile), server+brain launcher with EULA gate, diagnostic and mock smoke test.
- `docs`: architecture, limitations, acceptance and roadmap. `AGENTS.md` contains non-negotiable constraints.

## Execution workflow (act; do not merely propose)

1. Run `./scripts/doctor.sh`, `cd brain && npm test`, and `./scripts/smoke.sh`; fix failures.
2. With network and Java21/Maven3.9, run `./scripts/setup.sh`. Fix plugin dependency resolution and ALL Java compile errors against pinned Citizens 2.0.41 build 4138 / Paper 1.21.6 artifacts, with official API reference. Confirm actual plugin.yml command registration and event handlers.
3. Add Java-level tests of bounded task transitions, cancellation/recovery, and chest transactions where mock server or test fixtures permit.
4. Audit any non-atomic sequences. Implement a small durable operation journal or reconciled recovery mechanism so crashes between log block mutation, cargo persistence and chest inventory insertion do not silently duplicate or lose items. Add restart tests. Do not assert atomicity without tests.
5. Ensure a real villager moves correctly using Citizens pathfinding. Improve target selection, navigation failure detection, server tick performance, not breaking village buildings or player structures, chest-full recovery. Bound retries and avoid server chunk load spikes. Do not teleport during task navigation; the explicit operator `/ahmet summon` is allowed only while idle and must choose a loaded, safe block beside the operator.
6. Boot a fresh Paper 1.21.6 test world with Citizens build 4138 and the plugin. Test NPC spawn, single logs, 20 logs, chest binding, completion, cancellation, blocked paths, restart, player-owner authorization and two Java clients where available. Save logs/test results to `docs/VALIDATION_RESULTS.md`; clearly distinguish machine-tested from manual checks.
7. For live AI tests, use `TYPESAFE_API_KEY` only for the TypeSafe `/v1/systemone` JEV call and `OPENAI_API_KEY` only for the OpenAI `/v1/chat/completions` conversation call; never print either key. Verify a typed JEV action and one GPT-6 Luna conversation. Label heuristic/fallback vs Jev model in logs.
8. Repeat tests, ensure `npm test`, HTTP smoke, `mvn package` and GitHub Actions pass. Update README setup and `docs/ACCEPTANCE.md` checkboxes backed by evidence, with remaining blockers explicit.
9. Commit changes in a feature branch if git remote is configured. If there is no remote, create sensible local commits. Never force-push or overwrite existing work. Finish with exact commands to launch, acceptance evidence, remaining limitations and any required user inputs.

## Safety and source constraints

- Do not copy from `rmalde/minecraft-agent` without permission/license. Its design is a reference, not an authorized code source.
- The user explicitly requested offline-mode for this private LAN so the TLauncher client can connect. Keep whitelist enabled and do not expose/port-forward the server outside the trusted LAN. This changes player UUID identity; never expose the local brain API outside loopback or commit credentials.
- Default to private/whitelisted Paper server. Do not allow LLM-produced world coordinates, commands, or arbitrary action IDs to bypass validation.
- The current implementation has a server-side log removal/virtual cargo approximation, not native villager mining physics. Improve gesture/visual feedback when stable; document mechanics truthfully.
- Do not use a player with villager skin as a substitute. Real NPC entity type is a hard requirement.
- Protect irreplaceable saves: only test on newly created worlds.

## Final acceptance

Use the checklist in `docs/ACCEPTANCE.md` and return a short, honest evidence table. A success without a compiled plugin and observed game run is NOT 'plug and play'. If client/game automation is unavailable, deliver best verified server build and precise remaining manual instructions rather than claiming full completion.
