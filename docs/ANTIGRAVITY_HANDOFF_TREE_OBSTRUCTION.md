# Antigravity Handoff: Tree-Canopy Navigation Recovery

## Current state

- Repository: `/Users/aselekoglu/villager-ahmet`, branch `main`, HEAD `116acd0` (`fix: close task recovery and navigation review gaps`). Working tree was clean before this handoff note.
- No code changes have been made for this feature.
- `server-live` is running Paper 1.21.6 build 48, Citizens 2.0.41 build 4138, Java 21, and the latest built VillagerAhmet JAR (`d5d95ae0260c4a02ba00dec56070b611ba41883998d3f0fb51009dc0ef314f3e`). Brain listens on `127.0.0.1:3197`; Paper listens on port 25565. The prior plugin JAR is backed up at `server-live/plugin-backups/VillagerAhmet-400346320a4d55f3.jar.bak`.
- The user is actively testing in Minecraft. Do not restart or replace the live JAR while a player is connected. Preserve the private-LAN configuration, `online-mode=false`, whitelist, and all user world files.

## User request

Ahmet became stuck on top of a tree canopy; the user had to clear the way manually. They want Ahmet to consider breaking an obstruction when navigation gets stuck and choose based on the block type. Prefer the bounded TypeSafe JEV decision path; GPT-6 Luna feedback is optional only if it adds value. No arbitrary block coordinates or command passthrough may come from a model.

The screenshot showed Ahmet standing among leaf blocks above ground. Live log evidence around the report includes repeated `Navigation route gave up` entries, for example `npc=24,77,158`, `destination=29,73,158`, `cargo=9`, `collected=9/16`, followed by new JEV target selections around `(29,*,158)`. This confirms route failures but does not prove that leaves are the sole cause; an unreachable target coordinate or poor harvest approach may also contribute.

## Sol 6.1 review findings

1. **Try a safe harvest approach before mutation.** Targets currently use the block's own location; select a nearby safe walkable cell within harvest range first. This may resolve the canopy case without breaking anything.
2. **Never reuse the wood harvest path for leaves.** `harvest()` changes cargo/task counters and `OperationJournal.Harvest` accepts only gathered materials. Add a separate no-cargo `Clear` journal operation and include it in pending-operation gates, startup recovery, world-save accounting, and shutdown flushing. Recovery must fail closed on ambiguous before/after block data.
3. **Start with a narrow material policy.** Only consider natural, non-persistent, non-waterlogged leaves. Reject protected-home blocks, tracked player placements, logs, block entities, hazards, build materials, unloaded chunks, and unknown types. Placement provenance is currently tracked only for wood; extend protection for player-placed leaves or use a reliable persistent-leaves check plus explicit placement tracking.
4. **Prove a safe descent.** Clear side/head obstructions first. Removing a support leaf is allowed only if a bounded post-removal simulation finds a safe landing, clear body space, no fluid/hazard, and a conservative maximum fall (review suggested at most two blocks). Otherwise preserve the leaf and tell the player the route needs manual help.
5. **Bound and revalidate.** Offer a few Java-discovered candidate IDs such as `retry_route` and `clear_a`; JEV selects only an offered ID. Revalidate job generation, NPC position/reach, loaded chunk, exact block data, home protection, and landing on the server main thread immediately before mutation. Limit one removal per decision, three removals per route, and a small persisted per-job budget. JEV failure/fallback must not select a mutation.
6. Luna is unnecessary for routine decisions. Use JEV over typed options; deterministic player-facing feedback is safer when no candidate is safe.

## Relevant implementation points

- `paper-plugin/src/main/java/dev/ahmet/AhmetPlugin.java`: navigation watchdog/retry in `step()` (~390–430); harvest target selection and JEV candidate request (~480–565); `harvest()` (~583 onward); journal recovery (~650 onward); safe-ground checks (`isSafeGround` / `safeBlocks`, ~753); wood eligibility and protection (~844 onward); player placement tracking (`onPlayerPlaceWood`, ~335).
- `paper-plugin/src/main/java/dev/ahmet/NavigationWatchdog.java`: 15-second stall interval and give-up after three stalled attempts.
- `paper-plugin/src/main/java/dev/ahmet/NavigationTarget.java`: converts block locations to pathfinder destinations; inspect exact destination behavior.
- `paper-plugin/src/main/java/dev/ahmet/OperationJournal.java` and `OperationRecovery.java`: existing journal operations are harvest/deposit only.
- `paper-plugin/src/main/java/dev/ahmet/PlayerPlacedWoodRegistry.java`: coordinate tracking currently covers wood only.
- `brain/src/engine.mjs` and `brain/src/models.mjs`: `/api/decide` already accepts a bounded list of offered IDs (maximum 16); JEV chooses among them with an 8-second timeout. Reuse this interface; do not add Luna as a world-mutation authority.
- Existing tests include `NavigationWatchdogTest`, `NavigationTargetTest`, `OperationJournalTest`, `OperationRecoveryTest`, `SafeGroundPolicyTest`, and `PlayerPlacedWoodRegistryTest`.

## Proposed acceptance criteria

- A safe nearby approach is preferred over clearing blocks when it makes the log reachable.
- When still stuck, Ahmet considers only Java-approved local obstruction candidates and JEV can distinguish allowed block types.
- At most one bounded natural leaf is cleared per action; block clearing never changes collected/cargo/deposited counts or creates item drops.
- Player-placed/protected/unknown blocks and unsafe support leaves remain untouched; unsafe cases produce useful player feedback.
- A changed candidate, stale async decision, unloaded chunk, or crash at any journal stage cannot cause item creation/loss or ambiguous world mutation replay.
- Watchdog and clear budgets prevent endless retries.
- Add unit tests for material/protection classification, safe descent, async revalidation/bounds, clear journal crash states, and JEV/fallback behavior; then run the Java and Node suites. Live gameplay validation remains separate and requires the user to be online.

## Workflow and gate

The user requested **Sol 6.1 review → Luna implementation (medium effort) → Sol 6.1 implementation review → push to the existing GitHub remote**. Sol's first read-only review is complete and summarized above. Before changing code, present the concise design for user approval; the applicable brainstorming skill requires approval of the concrete design before implementation. After approval, implement and verify, then run the Sol 6.1 review and push only after that review is clean.

