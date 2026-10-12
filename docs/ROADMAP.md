# Villager Ahmet roadmap

## P0 — MVP acceptance, in order

1. Resolve Maven dependencies and compile plugin against pinned Paper/Citizens; add automated Java tests for logic where feasible.
2. Boot real private server; verify NPC entity, navigation, oak harvest, chest transaction with two clients.
3. Fix life-cycle semantics: persistence, restart, full chest, cancellation/recovery and stale in-flight model requests.
4. Run separate live checks: TypeSafe `/v1/systemone` for JEV and OpenAI `/v1/chat/completions` for GPT-6 Luna chat; record source/model without sensitive prompts.
5. Implement stricter tree detection and backoff when no reachable tree exists; include safety constraints for player builds.
6. Document a fully reproducible setup and perform a fresh-machine installation.

## P1 — Real villager immersion

- Citizen NPC schedule: wake, wander, work, sleep at bed and village home.
- Context-aware conversational memory; player relationships and session-scoped summaries.
- Avatar gestures: look at players, arm swing while chopping, working sounds, speech proximity.
- Perception: inventories/blocks in loaded chunks, player positions with privacy bounds.
- Durable task queue, crafting, delivery, farm harvesting, replanning and action verification.

## P2 — Multiple villagers

- Stable identity and roles, shared local economy, task delegation and controlled coordination.
- Admin UI/observability dashboard; audit actions, fuel/model cost and failure rate.
- Fine-grained land claims and plugin compatibility, bounded chunk loaders, recovery journal and permissions.

## Explicit non-goals for MVP

- Fully general Minecraft play, Ender Dragon runs, arbitrary code execution, unrestricted world edits, bypassing protected plots, human-equivalent crafting/combat, public-server abuse-resistance.
