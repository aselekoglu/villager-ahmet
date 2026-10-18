# Villager Ahmet — remaining roadmap completion

Scope: complete the remaining roadmap, not a plan-only deliverable. All implementation is delegated to GPT-6 Luna; the parent handles planning, evidence, review, publication, and deployment. Baseline main commit: `0ffce1a7f5bb8635b621217e6b0b6ca80f65774c`, clean, 155 Java/16 Node/mock smoke; current Java JAR `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1.

## Global Constraints

- Real Citizens VILLAGER, never a substitute player bot. Java owns authoritative jobs/world/inventory; Node owns language, memory and model selection.
- All Bukkit/Citizens access on Paper main thread; asynchronous HTTP bounded by timeouts. Keep pinned Paper 1.21.6/Citizens 2.0.41 b4138/Java21.
- No task/schedule teleport. Only explicit idle operator `/ahmet summon` repositions.
- Never manually edit/delete user worlds. All destructive/failure/runtime probes use fresh disposable servers. Preserve backups. Never automate EULA acceptance.
- No model keys read/copied/logged. No command passthrough or free model coordinates. Keep private LAN offline mode/whitelist and no WAN exposure.
- Typed offered actions, server validation, budgets, recovery/journal and task/cargo priority survive all features. New autonomous resource mutations are operator opt-in with finite budgets.
- User/player memory is isolated by actor; perception uses loaded, authorized, bounded local data. No cross-player chat disclosure, remote inventory browsing or arbitrary chunk loading.
- Every milestone has explicit bounded scope, tests and independent review before release. Only one implementation worker writes at a time. No unrelated refactors. Implementers never spawn reviewers.
- Distinguish unit/mock/fallback, disposable runtime, actual provider and human-player acceptance. A proxy or graceful restart never proves human gameplay or forced-crash durability.
- Existing authorization covers reviewed incremental main publications and graceful live deployments, even with players connected. Check/save/backup and verify installed hashes/status each time; no automatic summon or bed creation in user world.

## Milestones / dependency order

1. **Player/session conversational memory** (Node first vertical slice). Deterministic bounded player profile/interaction relationship, session boundaries and summaries; v1 migration; contextual model input; no additional model call per turn. Persist, reload, isolate two actors and verify through mock HTTP. Finish full Node suite and targeted independent review. Java authoritative contracts unchanged.
2. **Grounded perception contract**. Main-thread bounded snapshots of nearby loaded blocks, explicitly bound/authorized inventories and visible nearby players. Redact unrelated player data, enforce scan budgets and age/generation validation; typed Node context, no mutations. Tests and disposable proof of bounds/no chunk loads.
3. **Controlled production goal selection**. Connect observation-based harness goal choice to production under an operator opt-in policy; explicit jobs/cargo/recovery always win. Only offered typed goals/actions, finite per-tick/session budgets and no invented work. Replay tests, stale/cross-owner rejection, disposable transitions.
4. **Villager gestures and speech proximity**. Bounded nearby-player look, work swing/sound and proximity speech on main thread; task ownership and rate limits, no teleport. Disposable real-entity/event proof; human visual acceptance remains separate.
5. **Bounded crafting**. Operator/player requested allowlisted vanilla recipes (start logs→planks and planks→sticks), finite quantities, consume actual authorized inputs before output via durable transaction planning. No arbitrary recipe/command execution; no item duplication. Mixed inventory, full output, restart and conflict tests plus disposable real inventory proof.
6. **Farm harvesting**. Explicitly bound operator-approved plots and allowlisted mature crops (start wheat/carrot/potato), safe reach, typed actions, exact drops and seed/replant policy with transactional recovery. No player-build or unrelated crop changes. Disposable maturity, quota, cancellation/restart/full-chest tests.
7. **Multiple villagers and roles**. Stable persisted NPC identities; per-NPC home/bed/chest/task/state isolation; bounded worker/farmer/crafter role capabilities, controlled task delegation and resource ledger/local economy. Deterministic owner locks avoid duplicate work/input spend. No model-created NPC/permissions. Two actual Citizens villagers in disposable arena; cross-NPC restart/race/conflict tests.
8. **Claims/plugin compatibility/permissions**. Fine-grained operator land boundaries and action authorization, fail-closed existing protection integration and at least one explicitly documented compatible adapter. Explicit finite chunk tickets only if required, release ownership on cancellation/shutdown; never unbounded scanning/loading. Test foreign-owner refusal, missing adapter behavior and ticket cleanup in disposable runtime.
9. **Observability/admin interface**. Loopback/private authenticated bounded read-only dashboard by default; NPC/job state, audit events, failures and provider usage/cost reported only when known, unknown otherwise. Redact secrets and private chat, enforce budgets and retention. No free command execution. Node/browser/HTTP tests and actual UI verification; reuse existing runtime/dependencies where possible.
10. **Acceptance and reproducibility**. Fresh install in an isolated directory from documented dependencies; deterministic forced-crash and chest-interference tests in disposable world, cargo/journal proofs and regression scenarios. Rebuild distribution, checksum/ZIP integrity, final independent broad review, incremental main publish/deploy and live startup/save read-back. Human two-client/player acceptance requires the actual user's participation and stays unchecked until observed.

M1 can start immediately; M2 before M3 and M9; M3 before autonomous M5/M6/M7; M5/M6 before coordinated roles; all before M10 final completion. Read-only research/review may overlap, implementation never overlaps. For later milestones refine a small concrete contract from owning source/official pinned docs before writing code; do not treat this bounded MVP scope as general Minecraft autonomy.

## Release and evidence record

- Use `tasks/todo.md` and this plan's SDD ledger as the recovery map. Do not redispatch completed tasks after quota/compaction; inspect git and ledger first.
- Offline Java verification: `mvn -B -ntp -o -Dmaven.repo.local="<scratch Maven repository>" package` in `paper-plugin` with Java 21. Node: `node --test test/*.test.mjs`; mock smoke may require loopback permission.
- The current Node process was restarted from published source on 2026-10-18. Status read-back showed IDLE, zero cargo, bound chest, no bed, and an unspawned NPC; a walking status alone is not evidence of a spawned actor. The production memory file was not inspected.
- Disposable runtime helpers and dated evidence logs are local scratch artifacts; verify they exist before reuse. Prior evidence is archived outside the repository.

- Git HTTPS push previously failed; GitHub connector published identical tree hashes with expected-head non-force updates. API commits use author/committer offset -0400 and no terminal message newline; reconstruct and hash-check exact API commits locally before CAS ref sync. Current branch refs are clean/equal. Never invent a SHA or force-update a remote.
