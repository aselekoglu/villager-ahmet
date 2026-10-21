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

- [x] M3 controlled production goal selection and release: typed offered actions and server preconditions; explicit authorized jobs only; operator opt-in defaults off; bounded foreign-route wait/help, actual retry exhaustion, terminal route cleanup, safe delivery, and unloaded-bed fallback. Final source/runtime/package, normal source publication and matching remote SHA, and live deployment/read-back are recorded in [`VALIDATION_RESULTS.md`](VALIDATION_RESULTS.md). Human/provider/forced-crash acceptance remains open; M4 source/runtime/local package verification is complete, while publication and deployment remain pending; M5–M10 remain open.
- [x] Obstruction clearance & canopy navigation recovery: safe walkable approach, qualified leaf removal, safe descent simulation, and zero-cargo clear journal operation.
- [x] External protection SPI (`ProtectionHook`, `CompositeProtectionHook`) with fail-closed safety.
- [x] Structured player help requests (`BLOCKED_AWAITING_HELP`) when route retries or descent simulations fail.
- [x] Deterministic Citizens NPC schedule: WAKE/WORK at home, bounded WANDER, and bed-directed SLEEP with safe adjacency, fail-closed fallback, retries, status reasons, persistence, and task/cargo/recovery preemption. WANDER radius recovery retains the home route through the six-to-2.5-block zone and resumes wandering at home. WORK means idle availability, not auto-harvest. See [`ARCHITECTURE.md`](ARCHITECTURE.md) and [`ACCEPTANCE.md`](ACCEPTANCE.md); exact-binary runtime and player acceptance are tracked separately.
- [x] Bounded player/session conversational memory (M1): v1→v2 migration, isolated profiles, count-only familiarity labels, 30-minute idle rollover, a current summary and up to five deterministic prior summaries capped at 1,000 characters each, and allowlisted verified-event context. This is not semantic personal-fact extraction or an authority/permission system. Source implementation, local verification, Paper/Citizens package rebuild, source publication, and Node startup/read-back are complete. Live provider calls and human gameplay with the memory remain unverified; production-store migration was not inspected.
- [x] Grounded read-only perception (M2): bounded main-thread snapshots, opt-in admin-only bound-chest inventory, anonymous visible-player bands, Node sanitization, chat/JEV forwarding and stale chat-generation rejection. Source and disposable Paper acceptance passed; the four-entry package is verified at ZIP SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`. Source publication (`ba29c68916562f922b3bae085c72b51f345030c0`, tree `30d87eaf4c82778cfac5d4b9b87112129b600b76`), package verification, and live deployment/read-back are complete. Human/player visibility and live-provider checks were not performed.
- [x] M4 presentation source/runtime/local package: bounded idle look, best-effort arm/sound cues, nearby speech and actor-only replies passed source/unit review and the disposable Paper runtime gate. Corrected four-entry ZIP SHA-256 `6846225df2d99b40142f1ca377a014a964d40cf5fe4b3093745f21c5871691a0`. Human/client, provider and forced-crash acceptance remain open.
- [ ] M4 publication: publish the six public documentation changes and verify remote source SHA.
- [ ] M4 deployment: install the exact verified package and read back core hash, Paper/Node health, idle status and saved dimensions.
- [ ] Crafting, farm harvesting, multi-villager coordination.

## P2 — Multiple villagers

- Stable identity and roles, shared local economy, task delegation and controlled coordination.
- Admin UI/observability dashboard; audit actions, fuel/model cost and failure rate.
- Fine-grained land claims and plugin compatibility, bounded chunk loaders, recovery journal and permissions.

## Explicit non-goals for MVP

- Fully general Minecraft play, Ender Dragon runs, arbitrary code execution, unrestricted world edits, bypassing protected plots, human-equivalent crafting/combat, public-server abuse-resistance.
