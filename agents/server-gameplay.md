# Matrix3 Server Gameplay Rules

Read root `AGENTS.md` first. This file applies to server gameplay/content such as combat, NPC behavior, jobs, persistence-backed gameplay state, interactions, rewards, world logic, and content systems.

## Server authority

- Keep authoritative gameplay decisions server-side unless Matrix3 already documents another owner.
- Do not move gameplay authority into the client for convenience.
- Preserve established Matrix3 engine ownership and extend stable APIs rather than creating hidden parallel systems.
- Keep persistence explicit. Runtime state and saved state must have one clear owner and one clear load/save boundary.
- Prefer small vertical slices that can be tested and reverted independently.
- Avoid incidental changes to unrelated gameplay systems while implementing a focused content feature.
- When server behavior changes a shared/core path, define the relevant regression checks and use `docs/rs3/SMOKE_TEST.md` where required by root `AGENTS.md`.

## Cross-domain work

- For Construction/settlement gameplay, also read `agents/construction.md`.
- For player-facing interface work, also read `agents/interfaces.md`.
- For cache/data dependencies, also read `agents/cache-assets.md`.
