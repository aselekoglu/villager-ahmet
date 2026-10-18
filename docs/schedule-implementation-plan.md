# Deterministic Citizens schedule — 2026-10-16

The operator requested implementation, review, push to main and graceful live deployment in this session, using GPT-6 Luna implementers. Existing review fixes are released first. No world files are edited by hand. Runtime fixtures run only in a fresh disposable server.

## Global Constraints

- Real Citizens VILLAGER; all Bukkit/Citizens access on the Paper main thread.
- Task active, cargo > 0, recovery blocked or outstanding journal work immediately suspends schedule navigation/sleep. Accepted jobs wake the NPC and cancel only schedule-owned movement before task navigation begins.
- No teleport during task or schedule movement. Only operator `/ahmet summon` while idle may reposition.
- Deterministic world time modulo 24000: WAKE [0,1000), WORK [1000,9000), WANDER [9000,12000), SLEEP [12000,24000). WORK is idle availability at home, not autonomous harvesting.
- Small safe daytime wander around the existing home: radius at most 6 blocks, loaded chunks only, safe feet/head/floor, no block changes, bounded attempts and retry cooldowns. Return home for WAKE/WORK and at night.
- Bed is operator bound by looking at an existing bed within six blocks (`/ahmet bed`); persist only its location. Never create a bed or overwrite home. Walk to a safe adjacent standing cell before `Villager.sleep`; missing/occupied/unreachable bed safely falls back to resting at home with status explaining why. Do not use sleep to move the NPC from a distance.
- Private LAN stays online-mode=false, whitelist=true; no network changes. Do not read/copy/log model keys. Disposable brain uses sanitized mock environment and a fresh bridge token.

## Task 1: Correct and revalidate the existing runtime fixture

Ownership: scripts/fixtures/RuntimeCommonResourceProbe.java and evidence corrections in docs/ACCEPTANCE.md, docs/VALIDATION_RESULTS.md, CODEX_HANDOFF.md.

- Add an explicit JVM property `ahmet.disposableProbe=true` guard before any world mutation; otherwise disable the fixture without stopping the server.
- Run one scanner invocation per scheduler tick; use a continuation to start NPC jobs after completion. Measure actual server tick deltas, invocation timing, pending ticks and immediate invalidation. Fail and shut down only on an opted-in disposable server.
- Correct the historical 2026-10-16 synchronous-loop evidence to per-call measurements; leave actual responsiveness pending until controller records the new run. Remove contradictory current deployment wording.
- Compile fixture against pinned local runtime dependencies. Controller runs disposable acceptance, packages, commits/pushes and deploys existing production fixes before schedule implementation.

## Task 2: Pure schedule policy and bounded wander planner

Ownership: new paper-plugin/src/main/java/dev/ahmet/VillagerSchedule.java and VillagerScheduleTest.java.

- Pure deterministic phase and eligibility policy, no Bukkit calls; negative/overflow-safe modulo.
- Expose phaseAt(long), allowed(taskActive,cargo,recoveryBlocked,journalBusy), and findWanderWaypoint(Location current, Location home, int attempt, Predicate<Location> isSafe), returning null if worlds differ or no safe candidate exists. Try eight compass directions at radii 4 then 6 around home (dy 0,+1,-1), rotate deterministically by floorMod(attempt,8), enforce full 3D distance <=6 and bounded predicate calls. Keep integration state in a dedicated schedule adapter rather than growing the task loop.
- Test all exact phase boundaries and modulo, each busy/recovery/journal condition, deterministic bounded waypoint candidates, unsafe/no candidate behavior, world isolation. Run focused tests then full Java package. No commits by worker; controller reviews and commits.

## Task 3: Main-thread Citizens schedule integration

Ownership: dedicated schedule adapter, AhmetPlugin.java, config.yml, plugin.yml and integration-focused unit tests.

- Run schedule eligibility/preemption before any early return in step(). Clear only schedule-owned navigation and wake on job/cargo/recovery, phase changes, shutdown and operator reposition/home actions. Task starts must preempt within the same main-thread callback.
- Schedule adapter owns navigator watchdog/backoff and bounded deterministic waypoints, isolated from task navigation. Missing home or unspawned NPC remains idle. Manual return-home takes precedence until arrival.
- Bind existing bed with admin permission, loaded same-world and bounded reach validation; save/load location with normal plugin config. Expose phase/activity/reason in `/ahmet status` and document `/ahmet bed` in help/tab completion.
- Never call sleep until safely adjacent to validated head/foot bed, unoccupied, at SLEEP phase; avoid sharing another villager's bed or loading chunks. Wake on phase change and task admission. Safe failure remains at home; no mutations or model calls from schedule.
- Test preemption/transition/arrival/retry logic through pure seams. Compile against pinned Citizens/Paper API; inspect local source/javap for sleep semantics. Controller reviews before next task.

## Task 4: Disposable schedule acceptance and docs

Ownership: scripts/fixtures schedule probe (separate plugin descriptor) and README.md, CODEX_HANDOFF.md, docs/ARCHITECTURE.md, docs/ACCEPTANCE.md, docs/ROADMAP.md, docs/VALIDATION_RESULTS.md.

- Opt-in fixture, fresh disposable flat arena, actual Citizens walking across phases, bounded wander, at-home work/wake, walk-to-bed sleep and wake, task/cargo/recovery preemption, invalid/occupied/unreachable bed and restart persistence where feasible. Record actual tick observations and teleport events, do not infer runtime success from unit tests.
- Existing common-resource fixture remains valid with schedule preempted during jobs; run regression acceptance as needed. Controller builds fixtures, starts sanitized mock brain and fresh Paper, reviews logs, stops cleanly.
- Update docs with actual test counts, phase ranges, commands, safety and known unverified cases. Final broad review, full Java/Node/mock smoke, package/checksums, commit/push and graceful live deploy by controller.

## Review Focus

Task ownership must preempt schedule before watchdog early returns; sleep API must not reposition from afar; malformed/stale/wrong-world bed must fail closed; retries must not loop every tick; restart must preserve home/task/bed without editing user world data.
