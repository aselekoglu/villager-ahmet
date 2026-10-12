# Acceptance matrix — required before claiming MVP works

## Automated

- [x] Node test suite: parse Turkish/English oak job, reject invalid quantity, busy or absent chest, persist chat, validate action allowlist.
- [ ] CI Node job passes in connected environment.
- [x] Java 21 + Maven build succeeds using pinned Citizens and Paper dependencies.
- [x] Local mock HTTP smoke test passes (loopback permission enabled for the run).
- [ ] Two-person review of plugin Java API compatibility and no async Bukkit calls.
- [x] Integration coverage for `/api/chat`, `/api/decide`, `/api/event`, auth failures and labelled timeout fallback (mock/simulated provider timeout only).

## Real game (record results and logs, do not infer)

- [x] Fresh Paper 1.21.6 server boots with Citizens 2.0.41 build 4138 + VillagerAhmet enabled without exceptions.
- [ ] Two **legitimate Minecraft Java clients** connect on Java 1.21.6, with `online-mode=true` and whitelist active.
- [ ] Admin runs `/ahmet spawn`: an **EntityType.VILLAGER** appears, not a player with a skin; `/ahmet status` reports active.
- [ ] Admin `/ahmet summon` moves an existing idle Ahmet to a safe loaded block next to them; it is rejected during active work or recovery.
- [ ] Admin binds a chest by looking at it and running `/ahmet chest`; binding survives a server restart.
- [ ] Player A talks with `Ahmet, 20 meşe odunu topla`, receives reply and task start.
- [ ] Player B can ask Ahmet for status/chat, but cannot cancel Player A's task.
- [ ] Ahmet walks visibly on a flat path toward a real natural oak tree without teleporting.
- [ ] Exactly 20 real `OAK_LOG` blocks disappear; no extra item duplication, no structure damage.
- [ ] Ahmet walks to the bound chest and inserts exactly 20 `OAK_LOG` items, confirmed by inspection; final `/ahmet status` idle.
- [ ] Restart during job: task and cargo survive; eventual delivery conserves items.
- [ ] Chest full: Ahmet retains undelivered cargo and warns; after room is made `/ahmet deliver` works.
- [ ] Cancellation by task owner and admin succeeds, and cargo recovery does not lose or invent logs.
- [ ] NPC pathfinding failure gives actionable error, no infinite busy loop or unbounded chunk scan.
- [ ] Block modifications respect region protection in an appropriate test OR project explicitly remains private-only.
- [x] One **real** JEV decision is captured from TypeSafe with model id, selected action and safe server execution.
- [ ] One **real** LLM dialogue call succeeds in Turkish with direct OpenAI GPT-6 Luna.
- [ ] Secrets absent from git commits, Paper console logs and event memory data.

## Remaining blockers

1. The user has accepted the EULA. A fresh Paper server boot and game session remain unverified.
2. Two legitimate Java 1.21.6 clients and the real in-game spawn, movement, harvesting, deposit, restart, cancellation and chest-full scenarios remain unverified.
3. Java unit tests cover task bounds, cancellation, journal reload/replay, chest-slot planning and bounded navigation retries. Forced process-crash recovery with a live Paper world remains unverified; conflicting state deliberately pauses for manual review.
4. No Git remote is configured, so hosted GitHub Actions have not run. A local repository is being used for the handoff snapshot; equivalent Node, HTTP smoke, Maven and Java checks were run here.
5. A TypeSafe key has been copied privately into `brain/.env`; live JEV still needs a successful API call. `OPENAI_API_KEY` remains pending secure provisioning, so direct GPT-6 Luna chat is not live-tested yet.
6. Tree detection is an oak-log/nearby-leaves heuristic for private, trusted worlds. Region-protection integration and actual native villager mining/item carrying are not implemented.
