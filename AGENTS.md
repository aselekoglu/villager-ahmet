# Codex agent instructions for Villager Ahmet

Read `README.md`, `CODEX_HANDOFF.md`, `docs/ARCHITECTURE.md`, and `docs/ACCEPTANCE.md` before modifying code.

- Build a **real Citizens VILLAGER NPC**, never substitute a Mineflayer player in villager skin.
- The Java plugin owns world mutations and authoritative job state; Node owns language processing, memory and model selection.
- Do not lift code from `rmalde/minecraft-agent`: no license identified. Architectural inspiration only.
- Never edit or delete user worlds. Use a fresh testing `server/` and preserve backups.
- User must personally accept Minecraft EULA and supply credentials. Do not invent valid API keys, server logins or model test evidence.
- JEV uses TypeSafe `POST https://api.typesafe.ai/v1/systemone` with `TYPESAFE_API_KEY`; use an account-listed model such as `jev-latest`. Conversation uses OpenAI `POST https://api.openai.com/v1/chat/completions` separately with `OPENAI_API_KEY` and model `gpt-6-luna`. Always distinguish mock, fallback and live.
- Only an explicit operator `/ahmet summon` can reposition Ahmet to a safe nearby location, and only while idle; Citizens pathfinding must never teleport during normal task movement.
- Enforce bounded typed actions; no server command passthrough from LLM/JEV.
- All Bukkit/Citizens entity, world and inventory access on server main thread. HTTP requests asynchronous and time bounded.
- Do not finish with a plan only: compile, test, fix, document verification. Mark unexecuted acceptance items honestly.
- Use source-first troubleshooting of API versions; pin compatible Paper/Citizens versions instead of unreviewed upgrades.
