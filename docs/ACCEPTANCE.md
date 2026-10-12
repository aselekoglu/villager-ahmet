# Acceptance matrix — required before claiming MVP works

## Automated

- [x] Node test suite: parse Turkish/English oak job, reject invalid quantity, busy or absent chest, persist chat, validate action allowlist.
- [ ] CI Node job passes in connected environment.
- [x] Java 21 + Maven build succeeds using pinned Citizens and Paper dependencies.
- [x] Local mock HTTP smoke test passes (loopback permission enabled for the run).
- [ ] Two-person review of plugin Java API compatibility and no async Bukkit calls.
- [x] Integration coverage for `/api/chat`, `/api/decide`, `/api/event`, auth failures and labelled timeout fallback (mock/simulated provider timeout only).
- [x] Paper runtime exports every obtainable non-legacy 1.21.6 item as a canonical ID; live startup produced 1,414 unique item entries with no air entries. `/ahmet items <name>` was verified through Paper console dispatch; the player's in-game display has not been observed.
- [x] Common-resource chat intents validate canonical Minecraft IDs and bounded quantities; Paper's tested capability registry rejects unsupported IDs.
- [x] Common output/block/tool mappings, mixed cargo and harvest journal persistence pass Java unit tests; actual Paper drop-table/gameplay and forced restart behavior remain unverified.

## Real game (record results and logs, do not infer)

- [x] Fresh Paper 1.21.6 test world boots the final plugin with Citizens 2.0.41 build 4138; `/ahmet items raw_iron` and `/ahmet status` return successfully without plugin exceptions.
- [x] User save copied with a SHA-256 manifest to a separate Paper 1.21.6 server directory; source save preserved; imported-world boot completed; official account `aselekoglu` whitelisted and opped.
- [x] One Minecraft 1.21.6 client joined the imported world; operator `/ahmet summon` visibly placed the named Citizens villager beside the player.
- [x] `/ahmet chest` saved the delivery chest in plugin state.
- [ ] Mixed-wood collection gameplay: the screenshot session started an 8-log job, but the old oak-only plugin found no eligible oak and collected 0. The generalized log/stem implementation is now deployed; it still needs an in-game job run.
- [ ] Common-resource collection is verified through player chat and actual world harvesting/deposit. No gameplay collection test was run while the user was away.
- [ ] Two **legitimate Minecraft Java clients** connect on Java 1.21.6, with `online-mode=true` and whitelist active.
- [ ] Admin runs `/ahmet spawn`: an **EntityType.VILLAGER** appears, not a player with a skin; `/ahmet status` reports active.
- [ ] Admin `/ahmet summon` moves an existing idle Ahmet to a safe loaded block next to them; it is rejected during active work or recovery.
- [ ] Admin binds a chest by looking at it and running `/ahmet chest`; binding survives a server restart.
- [ ] Player A talks with `Ahmet, 20 meşe odunu topla`, receives reply and task start.
- [ ] Player B can ask Ahmet for status/chat, but cannot cancel Player A's task.
- [ ] Ahmet walks visibly on a flat path toward a selected natural log/stem candidate without teleporting.
- [ ] A mixed-wood job removes exactly the requested number of real log/stem blocks; no extra item duplication or obvious structure damage.
- [ ] Ahmet walks to the bound chest and inserts the exact per-material items collected, confirmed by inspection; final `/ahmet status` idle.
- [ ] Restart during job: task and cargo survive; eventual delivery conserves items.
- [ ] Chest full: Ahmet retains undelivered cargo and warns; after room is made `/ahmet deliver` works.
- [ ] Cancellation by task owner and admin succeeds, and cargo recovery does not lose or invent logs.
- [ ] NPC pathfinding failure gives actionable error, no infinite busy loop or unbounded chunk scan.
- [ ] Block modifications respect region protection in an appropriate test OR project explicitly remains private-only.
- [x] One **real** JEV choice call returns an offered action and actual model id from TypeSafe.
- [x] One **real** common-resource JEV call selected an offered candidate: `target_a`, source `jev`, model `jev-1.13.0`, confidence `0.98`.
- [ ] The Paper server safely executes that live JEV-selected action in-game.
- [ ] One **real** LLM dialogue call succeeds in Turkish with direct OpenAI GPT-6 Luna.
- [ ] Secrets absent from git commits, Paper console logs and event memory data.

## Remaining blockers

1. The user accepted the EULA. One Minecraft 1.21.6 client joined the imported save; `/ahmet summon` and chest binding were observed. Real harvesting, movement and deposit remain unverified.
2. The local server is at `10.0.0.140:25565`. The other computer reaches it, but its `BebekYiyen_69` TLauncher profile is offline and receives `Invalid session`. With `online-mode=true`, it requires a legitimate authenticated Java account; the offline profile cannot join. Two-client acceptance remains open.
3. Java unit tests cover task bounds, cancellation, journal reload/replay, chest-slot planning and bounded navigation retries. Forced process-crash recovery with a live Paper world remains unverified; conflicting state deliberately pauses for manual review.
4. No Git remote is configured, so hosted GitHub Actions have not run. A local repository is being used for the handoff snapshot; equivalent Node, HTTP smoke, Maven and Java checks were run here.
5. A TypeSafe key is stored privately in `brain/.env`; live wood and common-resource calls succeeded using `jev-latest` and returned actual model `jev-1.13.0`. Safe execution of those choices in Paper remains unverified. `OPENAI_API_KEY` is missing, so direct GPT-6 Luna chat is not live-tested.
6. The full item catalog and common-resource gather path are compiled, tested, and installed on the running imported-world server. Actual mixed-wood/common-resource harvesting, navigation and deposit remain unverified. The tree marker is heuristic, and region-protection integration and native villager mining/item carrying are not implemented.
