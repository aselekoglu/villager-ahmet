# Villager Ahmet roadmap

## Common-resource helper scope (current direction)

The 1.21.6 vanilla item catalog and common-resource code path are implemented; verify them in a disposable Paper world and fix any gameplay issues before adding rare blocks, crafting, mobs, Nether/End routines, or edge-case world mechanics. Collection capabilities remain opt-in and narrower than the catalog. Paper owns block/drop validation and the durable cargo journal; Node returns only a canonical item ID and quantity; JEV selects only among nearby, safe candidates offered by Paper. Unsupported catalog entries stay discoverable but non-executable.

## P0 — MVP acceptance, in order

1. Resolve Maven dependencies and compile plugin against pinned Paper/Citizens; add automated Java tests for logic where feasible.
2. Boot real private server; verify NPC entity, navigation, oak harvest, chest transaction with two clients.
3. Fix life-cycle semantics: persistence, restart, full chest, cancellation/recovery and stale in-flight model requests.
4. Run separate live checks: TypeSafe `/v1/systemone` for JEV and OpenAI `/v1/chat/completions` for GPT-6 Luna chat; record source/model without sensitive prompts.
5. Implement stricter tree detection and backoff when no reachable tree exists; include safety constraints for player builds.
6. Document a fully reproducible setup and perform a fresh-machine installation.

## P0.5 — Common resource collection

1. [x] Export and search the complete Paper 1.21.6 item catalog; recognition is separate from executable capabilities.
2. [x] Generalize chat intent to a bounded canonical item ID while retaining the separate TypeSafe JEV choice boundary.
3. [x] Add common block/drop mappings, minimum tools, bounded target scans, mixed-item cargo, and journal output-item persistence.
4. [x] Finish runtime tests for the item search command, actual Paper drop results, and each common collection group in a disposable Paper world.

## P1 — Real villager immersion & autonomy

- [x] AgentController / precondition harness: typed offered actions, deterministic server preconditions (`ActionPreconditions`), and replayable evaluation tests (`AgentEvaluationHarnessTest`). `AgentObservation` and `evaluateGoal` remain harness-only; the production task loop remains in `AhmetPlugin`.
- [x] Obstruction clearance & canopy navigation recovery: safe walkable approach, qualified leaf removal, safe descent simulation, and zero-cargo clear journal operation.
- [x] External protection SPI (`ProtectionHook`, `CompositeProtectionHook`) with fail-closed safety.
- [x] Structured player help requests (`BLOCKED_AWAITING_HELP`) when route retries or descent simulations fail.
- [x] Deterministic Citizens NPC schedule: WAKE/WORK at home, bounded WANDER, and bed-directed SLEEP with safe adjacency, fail-closed fallback, retries, status reasons, persistence, and task/cargo/recovery preemption. WORK means idle availability, not auto-harvest. See [`ARCHITECTURE.md`](ARCHITECTURE.md) and [`ACCEPTANCE.md`](ACCEPTANCE.md); final-binary disposable runtime and player acceptance are tracked separately.
- [ ] Context-aware conversational memory; player relationships and session-scoped summaries.
- [ ] Avatar gestures: look at players, arm swing while chopping, working sounds, speech proximity.
- [ ] Perception: inventories/blocks in loaded chunks, player positions with privacy bounds.
- [ ] Crafting, farm harvesting, multi-villager coordination.

## P2 — Multiple villagers

- Stable identity and roles, shared local economy, task delegation and controlled coordination.
- Admin UI/observability dashboard; audit actions, fuel/model cost and failure rate.
- Fine-grained land claims and plugin compatibility, bounded chunk loaders, recovery journal and permissions.

## Explicit non-goals for MVP

- Fully general Minecraft play, Ender Dragon runs, arbitrary code execution, unrestricted world edits, bypassing protected plots, human-equivalent crafting/combat, public-server abuse-resistance.
