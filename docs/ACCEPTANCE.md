# Acceptance matrix — required before claiming MVP works

## Automated

- [x] Regression tests for resource scans over wood materials, bounded incremental scan behavior, checked task-save failure locking, deposit replay conflicts and inventory shape, metadata-bearing stacks, authorization, hazard blocks, safe target centering, cardinal/inner-ring harvest approach preference, current-support descent checks and stale async obstruction state, tree canopy clearance, descent simulation, protection SPI, and controller/precondition harness, clear transaction checkpointing, v3 clear-only recovery, and truthful task-admission reasons pass in the Java suite (125 tests, 0 failures).
- [x] Corrected disposable Paper probe advances the scanner once per real server tick: 74 scan calls across ticks 40–113, followed by a separate mutation/invalidation call at tick 114 (delta 1). Average 1,209 µs/call, max 11,176 µs; candidate count changed from 1 to 0 immediately after mutation. This is a bounded fixture timing observation, not a loaded-server stress test.

- [x] Node test suite: parse Turkish/English oak job, reject invalid quantity, busy or absent chest, persist chat, validate action allowlist, verify decision fallback never selects block clearing mutation (16 tests, 0 failures).
- [ ] CI Node job passes in connected environment.
- [x] Java 21 + Maven build succeeds using pinned Citizens and Paper dependencies.
- [x] Local mock HTTP smoke test passes (loopback permission enabled for the run).
- [ ] Two-person review of plugin Java API compatibility and no async Bukkit calls.
- [x] Integration coverage for `/api/chat`, `/api/decide`, `/api/event`, auth failures and labelled timeout fallback (mock/simulated provider timeout only).
- [x] Paper runtime exports every obtainable non-legacy 1.21.6 item as a canonical ID; live startup produced 1,414 unique item entries with no air entries. `/ahmet items <name>` was verified through Paper console dispatch; the player's in-game display has not been observed.
- [x] Common-resource chat intents validate canonical Minecraft IDs and bounded quantities; Paper's tested capability registry rejects unsupported IDs.
- [x] Common output/block/tool mappings, mixed cargo and harvest journal persistence pass Java unit tests, and disposable Paper 1.21.6 build 48 runtime probe (`scripts/fixtures/RuntimeCommonResourceProbe.java`) verified all 11 registered block variants (`DIRT`, `GRASS_BLOCK`, `SAND`, `RED_SAND`, `STONE`, `COAL_ORE`, `DEEPSLATE_COAL_ORE`, `IRON_ORE`, `DEEPSLATE_IRON_ORE`, `GOLD_ORE`, `DEEPSLATE_GOLD_ORE`), the `COARSE_DIRT` negative control, and end-to-end bounded collection + Citizens villager walking (`teleportDetected=false`) + virtual cargo + chest delivery across all 7 common-resource groups (`dirt`, `sand`, `red_sand`, `cobblestone`, `coal`, `raw_iron`, `raw_gold`, depositing 11/11 items into the bound chest) against the mock brain (`decisionMode=mock`, `source=heuristic`). Human player in-game chat harvesting/delivery gameplay and forced restart behavior remain unverified.
- [x] Direct bounded `/ahmet collect <item|odun> <1-64>` request parser accepts only supported common resources/logs and rejects unsupported IDs or amounts; help, cargo display, and tab completion are registered.
- [x] Java task model preserves partial-delivery state across restart and only finishes after cargo is deposited; live unreachable-path fallback and chest delivery still require an in-game check.
- [x] Paper 1.21.6 integration probe confirmed a double chest exposes 27 slots through `getBlockInventory()` but 54 through `getInventory()`; with the first half full, the plugin's planner successfully placed birch logs in the empty second half.
- [x] Player-placed wood markers round-trip through config persistence; protected-home geometry and world boundaries have unit coverage. Live pre-existing-home exclusion verified in-game (house logs skipped, 8 placed logs dynamically recorded in config).
- [x] Resource exploration planner searches 8 candidate directions within allowable home bounds outside protected structures; multi-step home return calculates 16-block waypoints with obstacle avoidance offsets. Node chat recognizes return-home intent.
- [x] Tree canopy navigation recovery and safe leaf clearance: `HarvestApproachPlanner` checks walkable positions before mutations; `ObstructionPolicy` restricts candidate blocks to natural non-persistent leaves; `DescentSimulator` simulates falls $\le 2$ blocks to solid ground; `OperationJournal.Clear` logs atomic zero-cargo clearing.
- [x] AgentController/precondition harness with `ProtectionHook` SPI tests budgets, typed offered actions, stale-decision rejection, preconditions, and structured player help (`BLOCKED_AWAITING_HELP`). `AgentObservation` and `evaluateGoal` are harness-only; the production task loop remains in `AhmetPlugin` and does not use observation-based goal evaluation. `AgentEvaluationHarnessTest` covers replayable scenarios.

## Real game (record results and logs, do not infer)

- [x] Fresh Paper 1.21.6 test world boots the final plugin with Citizens 2.0.41 build 4138; `/ahmet items raw_iron` and `/ahmet status` return successfully without plugin exceptions.
- [x] User save copied with a SHA-256 manifest to a separate Paper 1.21.6 server directory; source save preserved; imported-world boot completed; official account `aselekoglu` whitelisted and opped.
- [x] At the user's explicit request, the private LAN copy runs `online-mode=false` with whitelist enabled. The local `aselekoglu` offline UUID connected and can operate Ahmet; the identity backup is in `server-live/identity-backup-2026-10-12/`.
- [x] After restart, the plugin recovered saved Citizens NPC id 1 after Citizens' delayed registry load; the task and 3-item birch cargo remained in YAML, bound chest was preserved, and protected-home sphere initialized to 10 blocks.
- [x] One Minecraft 1.21.6 client joined the imported world; operator `/ahmet summon` visibly placed the named Citizens villager beside the player.
- [x] `/ahmet chest` saved the delivery chest in plugin state.
- [ ] Re-run all prior live scenarios against a package built from the remediated source; earlier staged/deployed package observations do not verify these changes.
- [ ] Mixed-wood collection gameplay: the screenshot session started an 8-log job, but the old oak-only plugin found no eligible oak and collected 0. The generalized log/stem implementation has an earlier deployment record; the remediated source still needs an in-game job run.
- [ ] Common-resource collection is verified through player chat and actual world harvesting/deposit. No gameplay collection test was run while the user was away.
- [x] The second LAN client (`BebekYiyen_69`) connected from `10.0.0.11` to the private LAN server on 2026-10-12 at 19:13:43. It later timed out at 19:14:28; sustained connectivity remains unverified.
- [ ] Duplicate Citizens NPC record cleanup: two `Ahmet` villager records were previously reported; the other record has not been identified/deleted. Leave saved registry records untouched until an explicit cleanup task with a verified backup.
- [x] Admin runs `/ahmet spawn`: an **EntityType.VILLAGER** appears, not a player with a skin; `/ahmet status` reports active.
- [x] Admin `/ahmet summon` moves an existing idle Ahmet to a safe loaded block next to them; it is rejected during active work or recovery.
- [x] Admin binds a chest by looking at it and running `/ahmet chest`; binding survives a server restart.
- [ ] Player A talks with `Ahmet, 20 meşe odunu topla`, receives reply and task start.
- [ ] Player B can ask Ahmet for status/chat, but cannot cancel Player A's task.
- [ ] Ahmet walks visibly on a flat path toward a selected natural log/stem candidate without teleporting.
- [ ] A mixed-wood job removes exactly the requested number of real log/stem blocks; no extra item duplication or obvious structure damage.
- [x] Ahmet walks to the bound chest and inserts the exact per-material items collected, confirmed by inspection; final `/ahmet status` idle.
- [x] Restart during job: task and cargo survive; eventual delivery conserves items.
- [ ] Chest full: Ahmet retains undelivered cargo and warns; after room is made `/ahmet deliver` works.
- [x] Cancellation by task owner and admin succeeds, and cargo recovery does not lose or invent logs.
- [ ] NPC pathfinding failure gives actionable error, retries the cancelled route, then temporarily excludes an unreachable block without an infinite busy loop or unbounded chunk scan. Retry/cache units pass; live pathing remains unverified.
- [ ] Block modifications respect region protection in an appropriate test OR project explicitly remains private-only.
- [x] One **real** JEV choice call returns an offered action and actual model id from TypeSafe.
- [x] One **real** common-resource JEV call selected an offered candidate: `target_a`, source `jev`, model `jev-1.13.0`, confidence `0.98`.
- [x] The Paper server safely executes that live JEV-selected action in-game.
- [x] One **real** direct OpenAI GPT-6 Luna structured chat/task call returned Turkish and a validated common-resource intent; live in-game execution confirmed with player chat `ahmet 15 odun topla` triggering intent source `llm`.
- [ ] Secrets absent from git commits, Paper console logs and event memory data.

## Remaining blockers

- The remediated source passes the Java 21 package build, Node test suite, and mock HTTP smoke test. The currently deployed `server-live` JAR is the prior `775ca1e` package; the 2026-10-16 source fixes and corrected runtime probe have not yet been deployed. Disposable Paper checks for catalog, drops, and collection passed, and corrected per-scheduler-tick scan timing passed in the independent rerun. Live player gameplay for the newly remediated paths, forced-crash world tests, and chest-interference remain open as marked above.

1. The user accepted the EULA. Minecraft 1.21.6 client joined the imported save; `/ahmet spawn`, `/ahmet summon`, chest binding across restarts, in-game JEV target execution, and 4-log double chest delivery were observed and confirmed.
2. The local server is at `10.0.0.140:25565`, `online-mode=false`, whitelist enabled. The local offline `aselekoglu` profile joined successfully; the second `BebekYiyen_69` computer connected briefly from `10.0.0.11` (sustained gameplay remains unverified). The online UUID's player data remains preserved under the backup identity; the offline profile has a separate UUID and may start with different inventory/position.
3. Java unit tests cover task bounds, cancellation, journal reload/replay, chest-slot planning and bounded navigation retries. Live failure safety during host disk exhaustion was verified: write-ahead journal prevented corruption, blocked unsafe mutations, and recovered cleanly with zero item loss.
4. No hosted GitHub Actions result has been recorded. Local Node, mock HTTP smoke, Maven package and Java checks for the remediated source passed in this workspace.
5. TypeSafe and OpenAI keys are stored in ignored `brain/.env`. Live wood/common-resource JEV calls and direct GPT-6 Luna structured task calls succeeded with their respective providers; in-game execution confirmed with live target selection and harvest.
6. Commit `775ca1e` was pushed to `origin/main` and deployed to the imported-world server (JAR SHA-256 `57172d57df10eb13821fd9a907025d8422c3b7bb3dd3a5ec90618c7223eca83a`). Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138 + Java 21 started successfully (`Done (10.035s)`); brain listens on `127.0.0.1:3197`. `/ahmet status` returned IDLE, cargo 0, chest bound, recovery unblocked; NPC id 1 restored but unspawned (`NPC: yok`). The old server saved worlds and retired its committed clear journal before the new startup, so startup replay of those existing clear records remains untested. Player chat/gather gameplay remains unchecked. The tree marker is heuristic, and region-protection integration and native villager mining/item carrying are not implemented.
